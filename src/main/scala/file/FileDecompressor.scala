package file

/**
 * Decompresses a specified zip file to a specified location
 */
class FileDecompressor(zipFilePath: String, decompressedFilesDest: String) {
  import java.io.{ File, FileInputStream, FileOutputStream, BufferedInputStream, BufferedOutputStream }
  import java.util.zip.{ ZipInputStream, ZipEntry }
  import file.LogUtils.toLog

  /**
   * Performs the compression. The decompressed file name includes the time,
   * 	in nanoseconds, when it was created
   */
  def decompress = {
    println("Decompressing " + zipFilePath + "...")
    val currentTime = System.nanoTime().toString

    try {
      val fis = new FileInputStream(zipFilePath)
      val zis = new ZipInputStream(new BufferedInputStream(fis))

      var zipEntry: ZipEntry = null
      while ({ zipEntry = zis.getNextEntry(); zipEntry != null }) {

        // open output stream
        val BUFFER: Int = 2048
        val destFile = new File(decompressedFilesDest + File.separator + zipEntry.getName + "." + currentTime)
        new File(destFile.getParent()).mkdirs()
        val fos = new FileOutputStream(destFile)
        val dest = new BufferedOutputStream(fos, BUFFER)

        // extract zip file
        var count = 0
        var data: Array[Byte] = new Array[Byte](BUFFER)
        while ({ count = zis.read(data, 0, BUFFER); count != -1 }) {
          dest.write(data, 0, count)
        }

        dest.flush
        dest.close
        fos.flush()
        fos.close()
      }
      zis.close
    } catch {
      case e: Exception =>
        toLog("BATCH", zipFilePath, "VALIDATION", "ERROR", "Error when decompressing batch zip file")
    }

    try {
      val f = new File(zipFilePath)
      f.delete
    } catch { case e: Exception => }
    toLog("BATCH", zipFilePath, "VALIDATION", "ERROR", "Error when decompressing batch zip file")
  }
}

object RunApp extends App {
  import java.io.File

  def getFileTree(f: File): Stream[File] =
    f #:: (if (f.isDirectory) f.listFiles().toStream.flatMap(getFileTree)
    else Stream.empty)

  override def main(args: Array[String]) = {
    val zipFilesBaseDir = "C:/tmp/others_zipped"
    val destPath = "C:/tmp/others_copied"

    getFileTree(new File(zipFilesBaseDir)).foreach { f =>
      if (!f.isDirectory) {
        val decompressor = new FileDecompressor(f.getAbsolutePath(), destPath)
        decompressor.decompress
      }
    }
  }

}