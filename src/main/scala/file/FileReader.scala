package file

import scala.concurrent.Future
import akka.actor.{ Actor, ActorSystem, Props, ActorRef, Status, PoisonPill, Terminated }
import akka.routing.{ RoundRobinRouter, Broadcast, RoundRobinPool }
import scala.io.Source._
import java.io.File
import java.io.FileInputStream
import java.io.BufferedInputStream
import scala.collection.mutable.ArrayBuffer
import file.LogUtils._

/**
 * Messages
 */
case class StartProcessingFile(fileName: String)
case class ValidateFileName(fileName: String)
case class ValidateRecord(institutionType: Char, fileName: String, version: String, record: String)

/**
 * Gets a list of pending files
 */
class PendingFilesListActor(baseDir: String) extends Actor {
  import org.apache.commons.lang3.StringEscapeUtils.{ escapeJava }
  import FileReaderMaster._
  import scala.concurrent.Await
  import akka.util.Timeout
  import scala.concurrent.duration._
  import akka.pattern.ask
  import akka.dispatch.ExecutionContexts._
  import akka.dispatch.Futures
  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.collection.JavaConversions
  import org.apache.commons.io.FileUtils
  import utils.CfgUtils._
  import resource._

  implicit val timeout = Timeout(5 hours)

  val dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd#HH:mm:ss")

  def totalLines(f: java.io.File): Int = {
    try managed(io.Source.fromFile(f)) acquireAndGet { src => src.getLines.filterNot(_.isEmpty).size }
    catch {
      case e: Exception =>
        toLog("FILE", f.getName, "VALIDATION", "ERROR", "Error counting number of records in a file")
        0
    }
  }

  def receive = {
    case StartProcessingFile(fileName) => {
      var filesProcessed = 0;

      getFileTree(new File(baseDir)).foreach { f =>
        val cFileName = f.getName.split(escapeJava(System.getProperty("file.separator"))).last.trim

        if (!f.isDirectory() &&
          cFileName.matches("^CRB(B|M|D|S|F|Y)?[A-Za-z]{2}[0-9]{11}\\.[A-Za-z0-9]{4}\\.[A-Za-z0-9]{4}\\.\\d*")) {

          val _file = f
          val totalNumRecords = totalLines(_file).toString
          val destDir = new File(processingBaseDir)

          try FileUtils.moveFileToDirectory(f, destDir, true)
          catch { case e: Exception => }
          val fileToProcess = new File(processingBaseDir + "/" + f.getName())
          val fileReader = context.actorOf(Props(new FileReadMasterActor))
          val version: String = System.nanoTime().toString

          toLog("BATCH", f.getName() + "." + version, "VALIDATION", "INFO", "Processing started")
          val future: Future[String] = ask(fileReader, ProcessFile(fileToProcess.getAbsolutePath(), totalNumRecords)).mapTo[String]
          future.map { x =>
            toLog("BATCH", f.getName(), "VALIDATION", "INFO", "Processing complete")
            f.delete
          }

        }
      }
    }
  }
}

object FileReaderMaster {
  case class ProcessFile(filePath: String, totalNumRecords: String)
  case class ProcessLines(fileName: String, totalNumRecords: String, lines: List[String], last: Boolean = false)
  case class LinesProcessed(lines: List[String], last: Boolean = false)

  case object WorkAvailable
  case object GimmeeWork
}

class FileReadMasterActor extends Actor {
  import FileReaderMaster._

  val workChunkSize = 1
  val workersCount = 5

  def receive = waitingToProcess

  def waitingToProcess: Receive = {
    case ProcessFile(path, totalNumRecords) => {
      println("Starting...")
      val workers = (for (i <- 1 to workersCount) yield context.actorOf(Props[FileReaderActor])).toList
      val workersPool = context.actorOf(Props.empty.withRouter(RoundRobinRouter(routees = workers)))

      val inStream = new BufferedInputStream(new FileInputStream(path))
      val it = fromInputStream(inStream).getLines
      workersPool ! Broadcast(WorkAvailable)
      context.become(processing(path, totalNumRecords, it, workersPool, workers.size))

      //Setup deathwatch on all
      workers foreach (context watch _)

    }
  }

  def processing(fileName: String, version: String, it: Iterator[String], workers: ActorRef, workersRunning: Int): Receive = {
    case ProcessFile(path, totalNumRecords) =>
      sender ! Status.Failure(new Exception("already processing!!!"))

    case GimmeeWork if (it.hasNext) =>

      val lines = List.fill(workChunkSize) {
        if (it.hasNext) Some(it.next)
        else None
      }.flatten

      sender ! ProcessLines(fileName, version, lines, it.hasNext)

      //If no more lines, broadcast poison pill
      if (!it.hasNext) workers ! Broadcast(PoisonPill)

    //case GimmeeWork =>
    //get here if no more work left

    case LinesProcessed(lines, last) =>
    //Do something with the lines
    //toLog("BATCH", fileName, "VALIDATION", "INFO", "Processing complete")

    //Termination for last worker
    case Terminated(ref) if workersRunning == 1 =>
    //Done with all work, do what you gotta do when done here
    //toLog("BATCH", fileName, "VALIDATION", "INFO", "Processing terminated")

    //Terminared for non-last worker
    case Terminated(ref) =>
      context.become(processing(fileName, version, it, workers, workersRunning - 1))
  }
}

/**
 * Reads the specified file
 */
case class FileRecord(fileName: String, totalNumRecords: String, record: String)
class FileReaderActor() extends Actor {
  import FileReaderMaster._
  import akka.routing.RoundRobinRouter

  val recordValidator = context.actorOf(Props(new RecordValidationActor).withRouter(RoundRobinRouter(10)))

  def receive = {
    case ProcessLines(fileName, totalNumRecords, lines, last) => {
      lines.foreach { line =>
        toLog("BATCH", fileName, "VALIDATION", "INFO", "Validating: " + line)
        recordValidator ! FileRecord(fileName, totalNumRecords, line)
      }
      sender ! LinesProcessed(lines.map(_.reverse), last)
      sender ! GimmeeWork
    }

    case WorkAvailable =>
      println("WorkAvailable...")
      sender ! GimmeeWork
  }
}

/**
 * Entry point into the application
 */
object Init extends App {
  import scala.concurrent.duration._
  import scala.concurrent.ExecutionContext.Implicits.global
  import org.apache.camel.CamelContext
  import org.apache.camel.impl.DefaultCamelContext
  import org.apache.camel.scala.dsl.builder.RouteBuilder
  import utils.CfgUtils.{ remoteDataDir, localDataDir, localZipDir, FTP_USER, FTP_PASS, FTP_HOST, FTP_PORT }
  import file.LogUtils._
  import file.FileDecompressor
  import utils.CfgUtils.getFileTree
  // The actor system

  var fileProcessingStarted = false

  override def main(args: Array[String]) {
    //toLog("SYSTEM", "SYSTEM", "SYSTEM", "INFO", "System started")

    // Start the agent system
    val system = ActorSystem("FileReaderSystem")
    val readerActor = system.actorOf(Props(new PendingFilesListActor(localDataDir)))

    // Schedule tasks
    system.scheduler.schedule(0 seconds, 2 minutes)(startProcessing(readerActor))
    system.scheduler.schedule(0 seconds, 30 seconds)(writeLog)

    // Start FTP transfer pipeline
    val context: CamelContext = new DefaultCamelContext
    val routeBuilder = new RouteBuilder {
      from("ftp://" + FTP_USER + ":" + FTP_PASS + "@" + FTP_HOST + ":" + FTP_PORT +
        "?binary=true&recursive=true&move=.copied&delay=120000") to ("file:" + localZipDir)
    }

    context.addRoutes(routeBuilder)
    context.start()
    while (true) {}

  }

  /**
   * Decompresses and initiates processing of files
   */
  private def startProcessing(readerActor: ActorRef) = {
    try getFileTree(new File(localZipDir)).foreach { f =>
      if (!f.isDirectory) {
        val decompressor = new FileDecompressor(f.getAbsolutePath(), localDataDir)
        decompressor.decompress
      }
    } catch {
      case e: Exception =>
        toLog("BATCH", localZipDir, "VALIDATION", "ERROR", "Error when decompressing batch zip file")
    }

    readerActor ! StartProcessingFile("")
  }
}