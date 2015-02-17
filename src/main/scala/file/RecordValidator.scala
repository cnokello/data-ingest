package file

import akka.actor.{ Actor, Props }
import org.apache.commons.lang3.StringEscapeUtils.{ escapeJava }
import akka.actor.actorRef2Scala
import akka.util.Timeout
import file.LogUtils._
// import file.ValidateRecord
import utils.CfgUtils._
import validator.FileValidator
import scala.concurrent.duration.DurationInt

class RecordValidationActor extends Actor {
  import scala.util.matching.Regex
  import scala.concurrent.Await
  import akka.util.Timeout
  import scala.concurrent.duration._
  import akka.pattern.ask
  import akka.dispatch.ExecutionContexts._
  import akka.dispatch.Futures
  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val timeout = Timeout(30 minutes)

  val icValidator = context.actorOf(Props(ICValidator))
  val ciValidator = context.actorOf(Props(CIValidator))
  val caValidator = context.actorOf(Props(CAValidator))
  val crValidator = context.actorOf(Props(CRValidator))
  val giValidator = context.actorOf(Props(GIValidator))
  val siValidator = context.actorOf(Props(SIValidator))
  val faValidator = context.actorOf(Props(FAValidator))
  val bcValidator = context.actorOf(Props(BCValidator))

  def receive = {

    case FileRecord(fileName, totalNumRecords, record) => {
      val sep = System.getProperty("file.separator")
      val fileNameOnly = fileName.split(escapeJava(sep)).last.trim
      try {
        val instType = fileNameOnly.charAt(3)

        if ((fileNameOnly.matches("^CRB(B|M|D|S|F|Y)?[A-Za-z]{2}[0-9]{11}\\.[A-Za-z0-9]{4}\\.[A-Za-z0-9]{4}\\.\\d*")) == true) {

          // Individual consumer
          if (fileNameOnly.matches("^CRB(B|M|D|S|F|Y)?CE[0-9]{11}\\.[A-Za-z0-9]{4}\\.[A-Za-z0-9]{4}\\.\\d*")) {
            icValidator ! ValidateRecord(instType, fileNameOnly, totalNumRecords, record)

            // Corporate consumer
          } else if (fileNameOnly.matches("^CRB(B|M|D|S|F|Y)?CI[0-9]{11}\\.[A-Za-z0-9]{4}\\.[A-Za-z0-9]{4}\\.\\d*")) {
            ciValidator ! ValidateRecord(instType, fileNameOnly, totalNumRecords, record)

            // Credit applications
          } else if (fileNameOnly.matches("^CRB(B|M|D|S|F|Y)?CA[0-9]{11}\\.[A-Za-z0-9]{4}\\.[A-Za-z0-9]{4}\\.\\d*")) {
            caValidator ! ValidateRecord(instType, fileNameOnly, totalNumRecords, record)

            // Collaterals
          } else if (fileNameOnly.matches("^CRB(B|M|D|S|F|Y)?CR[0-9]{11}\\.[A-Za-z0-9]{4}\\.[A-Za-z0-9]{4}\\.\\d*")) {
            crValidator ! ValidateRecord(instType, fileNameOnly, totalNumRecords, record)

            // Guarantors
          } else if (fileNameOnly.matches("^CRB(B|M|D|S|F|Y)?GI[0-9]{11}\\.[A-Za-z0-9]{4}\\.[A-Za-z0-9]{4}\\.\\d*")) {
            giValidator ! ValidateRecord(instType, fileNameOnly, totalNumRecords, record)

            // Stakeholders
          } else if (fileNameOnly.matches("^CRB(B|M|D|S|F|Y)?SI[0-9]{11}\\.[A-Za-z0-9]{4}\\.[A-Za-z0-9]{4}\\.\\d*")) {
            siValidator ! ValidateRecord(instType, fileNameOnly, totalNumRecords, record)

            //Fraudulent activities
          } else if (fileNameOnly.matches("^CRB(B|M|D|S|F|Y)?FA[0-9]{11}\\.[A-Za-z0-9]{4}\\.[A-Za-z0-9]{4}\\.\\d*")) {
            faValidator ! ValidateRecord(instType, fileNameOnly, totalNumRecords, record)

            // Bounced cheques
          } else if (fileNameOnly.matches("^CRB(B|M|D|S|F|Y)?BC[0-9]{11}\\.[A-Za-z0-9]{4}\\.[A-Za-z0-9]{4}\\.\\d*")) {
            bcValidator ! ValidateRecord(instType, fileNameOnly, totalNumRecords, record)
          }
        }
      } catch { case e: Exception => }
    }
  }
}

object ICValidator extends Actor {

  def receive = {
    case ValidateRecord(instType, fileName, totalNumRecords, record) => {
      val validator = new FileValidator(instType, IC, IC_KAFKA_PRODUCER, IC_COLNUM)
      val recordF = record.split("\\|", -1)
      val valid = validator.isValid(fileName, recordF, totalNumRecords)
      toLog("BATCH", fileName, "VALIDATION", "INFO", "Validation status: " + valid + ", Record: " + record)

    }
    case _ => println("Unrecognized message")
  }
}

object CIValidator extends Actor {

  def receive = {
    case ValidateRecord(instType, fileName, totalNumRecords, record) => {
      val validator = new FileValidator(instType, CI, CI_KAFKA_PRODUCER, CI_COLNUM)
      val recordF = record.split("\\|", -1)
      val valid = validator.isValid(fileName, recordF, totalNumRecords)
      toLog("BATCH", fileName, "VALIDATION", "INFO", "Validation status: " + valid + ", Record: " + record)
    }
    case _ => println("Unrecognized message")
  }
}

object CAValidator extends Actor {

  def receive = {
    case ValidateRecord(instType, fileName, totalNumRecords, record) => {
      val validator = new FileValidator(instType, CA, CA_KAFKA_PRODUCER, CA_COLNUM)
      val recordF = record.split("\\|", -1)
      val valid = validator.isValid(fileName, recordF, totalNumRecords)
      toLog("BATCH", fileName, "VALIDATION", "INFO", "Validation status: " + valid + ", Record: " + record)
    }
    case _ => println("Unrecognized message")
  }
}

object CRValidator extends Actor {

  def receive = {
    case ValidateRecord(instType, fileName, totalNumRecords, record) => {
      val validator = new FileValidator(instType, CR, CR_KAFKA_PRODUCER, CR_COLNUM)
      val recordF = record.split("\\|", -1)
      val valid = validator.isValid(fileName, recordF, totalNumRecords)
      toLog("BATCH", fileName, "VALIDATION", "INFO", "Validation status: " + valid + ", Record: " + record)
    }
    case _ => println("Unrecognized message")
  }
}

object FAValidator extends Actor {

  def receive = {
    case ValidateRecord(instType, fileName, totalNumRecords, record) => {
      val validator = new FileValidator(instType, FA, FA_KAFKA_PRODUCER, FA_COLNUM)
      val recordF = record.split("\\|", -1)
      val valid = validator.isValid(fileName, recordF, totalNumRecords)
      toLog("BATCH", fileName, "VALIDATION", "INFO", "Validation status: " + valid + ", Record: " + record)
    }
    case _ => println("Unrecognized message")
  }
}

object BCValidator extends Actor {

  def receive = {
    case ValidateRecord(instType, fileName, totalNumRecords, record) => {
      val validator = new FileValidator(instType, BC, BC_KAFKA_PRODUCER, BC_COLNUM)
      val recordF = record.split("\\|", -1)
      val valid = validator.isValid(fileName, recordF, totalNumRecords)
      toLog("BATCH", fileName, "VALIDATION", "INFO", "Validation status: " + valid + ", Record: " + record)
    }
    case _ => println("Unrecognized message")
  }
}

object GIValidator extends Actor {

  def receive = {
    case ValidateRecord(instType, fileName, totalNumRecords, record) => {
      val validator = new FileValidator(instType, GI, GI_KAFKA_PRODUCER, GI_COLNUM)
      val recordF = record.split("\\|", -1)
      val valid = validator.isValid(fileName, recordF, totalNumRecords)
      toLog("BATCH", fileName, "VALIDATION", "INFO", "Validation status: " + valid + ", Record: " + record)
    }
    case _ => println("Unrecognized message")
  }
}

object SIValidator extends Actor {

  def receive = {
    case ValidateRecord(instType, fileName, totalNumRecords, record) => {
      val validator = new FileValidator(instType, SI, SI_KAFKA_PRODUCER, SI_COLNUM)
      val recordF = record.split("\\|", -1)
      val valid = validator.isValid(fileName, recordF, totalNumRecords)
      toLog("BATCH", fileName, "VALIDATION", "INFO", "Validation status: " + valid + ", Record: " + record)
    }
    case _ => println("Unrecognized message")
  }
}