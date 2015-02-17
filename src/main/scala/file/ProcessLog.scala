package file

import org.apache.camel.Processor

/**
 * A log object
 *
 * authDate: Date and time of log generation
 * logLevel: Possible values include SYSTEM, BATCH, FILE, RECORD
 * batchDate: Date assigned to the batch
 * instCode: Institution code for the batch
 * instType: Example include BNK, MFB, DPFB, MFI
 * submissionDate: Submission date in the file name
 * fileName: The full file name (does NOT include absolute path)
 * fileType: Possible values include IC, CI, SI, GI, CR, CA, BC, FA
 * logType: Possible values include RESOURCE, VALIDATION
 * logSubType: Possible values include INFO, ERROR
 *
 */
case class ProcessLog(
  authDate: String,
  logLevel: String,
  batchDate: String,
  instCode: String,
  instType: String,
  submissionDate: String,
  fileName: String,
  fileType: String,
  logType: String,
  logSubType: String,
  logMessage: String) {

  override def toString(): String = authDate + "|" + logLevel + "|" + batchDate +
    "|" + instCode + "|" + instType + "|" + submissionDate + "|" + fileName + "|" +
    fileType + "|" + logType + "|" + logSubType + "|" + logMessage

}

object LogUtils {
  import scala.collection.mutable.ArrayBuffer
  import org.apache.commons.io.FileUtils
  import java.io.File
  import utils.CfgUtils.logBaseDir

  var logs: ArrayBuffer[String] = new ArrayBuffer[String]()

  /**
   * Creates a log record
   */
  def toLog(logLevel: String, _fileName: String, logType: String, logSubType: String, logMessage: String) = {
    val dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    val authDate = dateFormat.format(new java.util.Date)
    val fileNameComps = _fileName.split("\\.")
    var fileName = ""
    var batchDate = ""
    var instCode = ""
    var instType = ""
    var fileType = ""
    var submissionDate = ""

    try {
      fileName = fileNameComps(0) + "." + fileNameComps(1)
      instCode = fileNameComps(1)
      batchDate = fileNameComps(2)
      instType = fileName.substring(3, 3)
      fileType = fileName.substring(4, 5)
      submissionDate = fileName.substring(6, 13)
    } catch { case e: Exception => }

    val log = new ProcessLog(
      authDate.toString,
      logLevel,
      batchDate,
      instCode,
      instType,
      submissionDate,
      fileName,
      fileType,
      logType,
      logSubType,
      logMessage)

    try {
      logs append (log.toString)
      if (logs.size % 100 == 0) {
        writeLog
      }
    } catch { case e: Exception => println(_fileName + e.printStackTrace) }
  }

  /**
   * Writes logs to a file
   */
  def writeLog = {
    if (logs.size > 0) {
      try FileUtils.writeStringToFile(new File(logBaseDir + "/dl.log"), logs.mkString("\n") + "\n", true)
      catch { case e: Exception => println(e.printStackTrace) }
      logs.clear
    }
  }

}