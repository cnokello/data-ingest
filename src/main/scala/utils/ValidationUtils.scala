package utils

import org.slf4j.LoggerFactory
import java.util.Properties
import scala.collection.mutable.Map
import java.io.FileInputStream
import kafka.producer._
import java.io.File

object CfgUtils {
  val cfgBaseDir = "/home/svc_cis4/dl/cfg/"
  val remoteDataDir = "C:/tmp/others/raw"

  val globalProps = new Properties
  globalProps.load(new FileInputStream(cfgBaseDir + "validator.properties"))

  val localZipDir = globalProps.getProperty("dir.data.zip")
  val localDataDir = globalProps.getProperty("dir.data.local")
  val processingBaseDir = globalProps.getProperty("dir.processing")
  val logBaseDir = globalProps.getProperty("dir.logs")
  val logger = LoggerFactory.getLogger(getClass)

  // FTP Server Config
  val FTP_HOST = globalProps.getProperty("ftp.host")
  val FTP_PORT = globalProps.getProperty("ftp.port")
  val FTP_DIR = "ftp"
  val FTP_USER = globalProps.getProperty("ftp.user")
  val FTP_PASS = globalProps.getProperty("ftp.passwd")

  // File type codes
  val IC = "ic"
  val CI = "ci"
  val CA = "ca"
  val CR = "cr"
  val GI = "gi"
  val SI = "si"
  val FA = "fa"
  val BC = "bc"

  // Institution type codes
  val BANK = 'B'
  val DPFB = 'D'
  val MFB = 'B'
  val MFI = 'M'
  val SACCO = 'S'

  // Number of columns
  val IC_COLNUM = 67
  val CI_COLNUM = 58
  val CA_COLNUM = 25
  val SI_COLNUM = 37
  val GI_COLNUM = 40
  val CR_COLNUM = 26
  val BC_COLNUM = 11
  val FA_COLNUM = 15

  // Validation error classes
  val ERROR_TYPE_COLNUM = "INVALID NUMBER OF COLUMNS"
  val ERROR_TYPE_CLASS1 = "VALIDATION ERROR CLASS 1"
  val EMPTY_VALUE_REGEX = "(NA|NONE|NULL|[^A-Za-z0-9])"

  // KAFKA communication config
  val KAFKA_HOSTS = globalProps.getProperty("hosts.kafka")
  val IC_TOPIC = "IC"
  val IC_CONSUMER_GRP = "IC"
  val CI_TOPIC = "CI"
  val CI_CONSUMER_GRP = "CI"
  val SI_TOPIC = "SI"
  val SI_CONSUMER_GRP = "SI"
  val GI_TOPIC = "GI"
  val GI_CONSUMER_GRP = "GI"
  val BC_TOPIC = "BC"
  val BC_CONSUMER_GRP = "BC"
  val CA_TOPIC = "CA"
  val CA_CONSUMER_GRP = "CA"
  val CR_TOPIC = "CR"
  val CR_CONSUMER_GRP = "CR"
  val FA_TOPIC = "FA"
  val FA_CONSUMER_GRP = "FA"

  // Kafka Producers
  val IC_KAFKA_PRODUCER = new KafkaProducer(IC_TOPIC, KAFKA_HOSTS)
  val CI_KAFKA_PRODUCER = new KafkaProducer(CI_TOPIC, KAFKA_HOSTS)
  val SI_KAFKA_PRODUCER = new KafkaProducer(SI_TOPIC, KAFKA_HOSTS)
  val GI_KAFKA_PRODUCER = new KafkaProducer(GI_TOPIC, KAFKA_HOSTS)
  val CA_KAFKA_PRODUCER = new KafkaProducer(CA_TOPIC, KAFKA_HOSTS)
  val CR_KAFKA_PRODUCER = new KafkaProducer(CR_TOPIC, KAFKA_HOSTS)
  val BC_KAFKA_PRODUCER = new KafkaProducer(BC_TOPIC, KAFKA_HOSTS)
  val FA_KAFKA_PRODUCER = new KafkaProducer(FA_TOPIC, KAFKA_HOSTS)

  def loadProperties(instType: Char, fileType: String): Properties = {
    val props = new Properties
    props.load(new FileInputStream(cfgBaseDir +
      fileType.toLowerCase + "." + instType.toLower + ".properties"))

    val msgProps = new Properties
    msgProps.load(new FileInputStream(cfgBaseDir + "msg/en.properties"))
    props.putAll(msgProps)

    return props
  }

  def getAccountNumber(fileType: String, record: Array[String]): String =
    if (fileType.trim.toLowerCase equals IC) try record(7) catch { case e: Exception => "UKNOWN" }
    else if (fileType.trim.toLowerCase equals CI) try record(7) catch { case e: Exception => "UKNOWN" }
    else if (fileType.trim.toLowerCase equals CA) try record(12) catch { case e: Exception => "UKNOWN" }
    else if (fileType.trim.toLowerCase equals CR) try record(5) catch { case e: Exception => "UKNOWN" }
    else if (fileType.trim.toLowerCase equals SI) try record(9) catch { case e: Exception => "UKNOWN" }
    else if (fileType.trim.toLowerCase equals GI) try record(5) catch { case e: Exception => "UKNOWN" }
    else if (fileType.trim.toLowerCase equals FA) try record(5) catch { case e: Exception => "UKNOWN" }
    else if (fileType.trim.toLowerCase equals BC) try record(2) catch { case e: Exception => "UKNOWN" }
    else "UKNOWN"

  @throws(classOf[Exception])
  def getFileTree(f: File): Stream[File] =
    f #:: (if (f.isDirectory) f.listFiles().toStream.flatMap(getFileTree)
    else Stream.empty)

}