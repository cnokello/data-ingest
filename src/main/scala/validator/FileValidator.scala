package validator

import kafka.producer.KafkaProducer

class FileValidator(instType: Char, fileType: String, kafkaProducer: KafkaProducer, numCols: Int) {
  import org.slf4j.LoggerFactory
  import java.util.Properties
  import java.util.UUID
  import java.io.FileInputStream
  import scala.collection.mutable.ListBuffer
  import scala.collection.mutable.Map
  import org.apache.commons.lang3.StringEscapeUtils.escapeJava
  import org.json4s.native.Serialization.write
  import org.json4s.DefaultFormats
  import utils.CfgUtils._

  val logger = LoggerFactory.getLogger(getClass)

  val dateFormat = new java.text.SimpleDateFormat("yyyyMMdd")

  var tuRequiredI = ListBuffer[Int]()
  var kbaRequiredI = ListBuffer[Int]()
  var dataTypes: Map[String, List[Int]] = Map[String, List[Int]]()
  var businessTypes: Map[String, List[Int]] = Map[String, List[Int]]()
  var conditionalRules: Map[String, Array[String]] = Map[String, Array[String]]()
  var errorMessages: Map[String, String] = Map[String, String]()

  val props = loadProperties(instType, fileType)
  val SEP = "|"

  /**
   * Load mandatory field rules
   */
  props.getProperty("tu.required").split(",").foreach { f =>
    if (!(f.isEmpty())) tuRequiredI.append(props.getProperty(f.trim).toInt)
  }

  props.getProperty("kba.required").split(",").foreach { f =>
    if (!(f.isEmpty())) kbaRequiredI.append(props.getProperty(f.trim).toInt)
  }

  /**
   * Load error messages
   */
  props.getProperty("msg.error").split(";").foreach { p =>
    if (p != null && !p.isEmpty()) {
      val msg = p.split("=")

      if (msg.size == 2) {
        //logger.info(msg.mkString(": "))
        errorMessages put (msg(0).trim, msg(1).trim)
      }
    }
  }

  /**
   * Load data type rules
   */
  props.getProperty("datatype.rules").split(";").foreach { rule =>
    val r = rule.split("=")
    val ruleName = r(0)

    var ruleFields = ListBuffer[Int]()
    r(1).split(",").foreach { f => ruleFields.append(props.getProperty(f.trim).toInt) }
    dataTypes put (ruleName, ruleFields.toList)
  }

  /**
   * Load business type rules
   */
  props.getProperty("business.rules").split(";").foreach { rule =>
    val r = rule.split("=")
    val ruleName = r(0)

    var ruleFields = ListBuffer[Int]()
    r(1).split(",").foreach { f => ruleFields.append(props.getProperty(f.trim).toInt) }
    businessTypes put (ruleName.trim, ruleFields.toList)
  }

  /**
   * Load conditional rules
   */
  val condRulesProps = props.getProperty("conditional.rules")
  if (condRulesProps != null) {
    condRulesProps.split(";").foreach { cond =>
      val r = cond.split(":")
      val ruleName = r(0)

      // process condition
      if (r.size >= 2) {
        val ruleCond = r(1).split("THEN")
        val condLeft = ruleCond(0)
        val condRight = ruleCond(1)

        // Split into parts
        val condParts = splitToParts(condLeft) ++ splitToParts(condRight)
        conditionalRules put (ruleName.split("\\.")(1).trim, condParts)

        //logger.info("RULE: " + cond)
        conditionalRules.foreach { e => logger.info(e._1 + ": " + e._2.mkString(", ")) }
      }
    }
  }

  private def splitToParts(cond: String): Array[String] = {
    var condParts = Array[String]()
    val condLeftEq = cond.split("=EQ")
    if (condLeftEq.size == 2) {
      var field1 = condLeftEq(0).replace("IF", "").trim
      var field2 = condLeftEq(1).trim
      field1 = props.getProperty(field1)
      if (field1 == null) field1 = condLeftEq(0).replace("IF", "").trim

      if (!field2.trim.matches("[0]+")) field2 = props.getProperty(field2)
      if (field2 == null) field2 = condLeftEq(1).trim

      condParts = Array(field1, field2, "=")
    } else {
      val condLeftNEq = cond.split("=NEQ")
      if (condLeftNEq.size == 2) {
        var field1 = condLeftNEq(0).replace("IF", "").trim
        var field2 = condLeftNEq(1).trim

        field1 = props.getProperty(field1)
        if (field1 == null) field1 = condLeftNEq(0).replace("IF", "").trim

        if (!field2.trim.matches("[0]+")) field2 = props.getProperty(field2)
        if (field2 == null) field2 = condLeftNEq(1).trim

        condParts = Array(field1, field2, "!=")
      } else {
        val condLeftGr = cond.split("=GT")
        if (condLeftGr.size == 2) {
          var field1 = condLeftGr(0).replace("IF", "").trim
          var field2 = condLeftGr(1).trim

          field1 = props.getProperty(field1)
          if (field1 == null) field1 = condLeftGr(0).replace("IF", "").trim

          if (!field2.trim.matches("[0]+")) field2 = props.getProperty(field2)
          if (field2 == null) field2 = condLeftGr(1).trim

          condParts = Array(field1, field2, ">")
        } else {
          val condLeftLe = cond.split("=LT")
          if (condLeftLe.size == 2) {
            var field1 = condLeftLe(0).replace("IF", "").trim
            var field2 = condLeftLe(1).trim
            field1 = props.getProperty(field1)
            if (field1 == null) field1 = condLeftLe(0).replace("IF", "").trim

            if (!field2.trim.matches("[0]+")) field2 = props.getProperty(field2)
            if (field2 == null) field2 = condLeftLe(1).trim

            condParts = Array(field1, field2, "<")
          } else {
            val condLeftGeq = cond.split("=GEQ")
            if (condLeftGeq.size == 2) {
              var field1 = condLeftGeq(0).replace("IF", "").trim
              var field2 = condLeftGeq(1).trim
              field1 = props.getProperty(field1)
              if (field1 == null) field1 = condLeftGeq(0).replace("IF", "").trim

              if (!field2.trim.matches("[0-9]+")) field2 = props.getProperty(field2)
              if (field2 == null) field2 = condLeftGeq(1).trim

              condParts = Array(field1, field2, ">=")
            }
          }
        }
      }
    }

    return (condParts)
  }
  /**
   * Composes error message
   */
  private def errorMessage(fileName: String, affectedField: String, providedValue: String,
    errorMessage: String, errorType: String, advice: String): String = {
    var fieldLabel = ""
    if (affectedField != null && affectedField.contains("and")) {
      val fields = affectedField.split("and")
      if (fields.length == 2)
        fieldLabel = props.getProperty(fields(0).trim) + " and " + props.getProperty(fields(1).trim)
    } else fieldLabel = props.getProperty(affectedField.trim)

    return (fileName + SEP + affectedField + SEP + providedValue + SEP +
      errorMessage + SEP + advice + SEP + errorType + SEP + fieldLabel)
  }

  /**
   * Validates conditional rules
   */
  private def isValidConditional(fileName: String, record: Array[String]): List[String] = {
    //logger.info("Performing conditional validation ######## File Name: " + fileName + " \n# Record: " + record.mkString(","))

    val errors = ListBuffer[String]()
    conditionalRules.foreach { e =>
      try {
        val left1 = e._2(0)
        val left2 = e._2(1)
        val leftRelation = e._2(2)

        val right1 = e._2(3)
        val right2 = e._2(4)
        val rightRelation = e._2(5)

        if (isValidCondition(record, left1, left2, leftRelation) && !isValidCondition(record, right1, right2, rightRelation)) {
          val errorMsg = (errorMessages.get(e._1.trim).getOrElse("").asInstanceOf[String]).split(":")
          errors.append(errorMessage(fileName, left1 + " and " + right1.trim, record(left1.trim.toInt)
            + " and " + record(right1.trim.toInt), errorMsg(1).trim, errorMsg(0).trim, errorMsg(2).trim))
        }

      } catch { case e: Exception => }
    }

    return (errors.toList)
  }

  private def isValidCondition(record: Array[String], var1: String, var2: String, relation: String): Boolean = {
    var valid = false
    var field2: Any = var2
    try field2 = record(var2.trim.toInt).trim
    catch { case e: Exception => field2 = var2 }

    if (relation.trim.equals("=")) {
      if (var2 != null && var2.trim.equals("NULL")) {
        try {
          if (record(var1.trim.toInt).trim.matches(EMPTY_VALUE_REGEX))
            valid = true
        } catch { case e: Exception => }

      } else if (var2 != null && var2.trim.matches("[0]+") || var2.trim.equals("999")) {
        try {
          if (record(var1.trim.toInt) equals ("0"))
            valid = true
        } catch { case e: Exception => }
      } else if (var2 != null && var2.trim.matches("[A-Za-z]")) {
        try {
          if (record(var1.trim.toInt).trim.toUpperCase.equals(var2.trim.toUpperCase))
            valid = true
        } catch { case e: Exception => }
      } else {
        try { if (record(var1.trim.toInt).trim.equals(record(var2.trim.toInt).trim)) valid = true }
        catch {
          case e: Exception => logger.error("ERROR: An error occurred while doing conditional comparison. Field Value: "
            + record(var1.trim.toInt))
        }
      }

    } else if (relation.trim.equals(">")) {
      if (var2 != null && var2.trim.matches("[0]+")) {
        try {
          val var1Val = record(var1.trim.toInt).trim
          if (var1Val != null && !var1Val.isEmpty && var1Val.toDouble > 0) valid = true
        } catch { case e: Exception => }
      } else {
        try { if (record(var1.trim.toInt).trim.toLong > record(var2.trim.toInt).trim.toLong) valid = true }
        catch {
          case e: Exception => logger.error("ERROR: An error occurred while doing conditional comparison.: Indexes: "
            + var1.trim + " > " + var2.trim + "\n")
        }
      }

    } else if (relation.trim.equals(">=")) {
      if (var2 != null && var2.trim.matches("[0]+")) {
        try {
          val var1Val = record(var1.trim.toInt).trim
          if (var1Val != null && !var1Val.isEmpty && var1Val.toLong >= 0) valid = true
        } catch { case e: Exception => }
      } else {
        try if (record(var1.trim.toInt).trim.toLong >= record(var2.trim.toInt).trim.toLong) valid = true
        catch {
          case e: Exception => logger.error("ERROR: An error occurred while doing conditional comparison.: Indexes: "
            + var1.trim + "  >= " + var2.trim + "\n")
        }
      }

    } else if (relation.trim.equals("<")) {
      if (var2 != null && var2.trim.matches("[0]+")) {
        try { if (record(var1.toInt).toLong < 0) valid = true } catch { case e: Exception => }
      } else {
        try { if (record(var1.toInt).trim.toLong < record(var2.toInt).trim.toLong) valid = true }
        catch {
          case e: Exception => logger.error("ERROR: An error occurred while doing conditional comparison.: Indexes: " +
            var1.trim + "<" + var2.trim)
        }
      }

    } else if (relation.trim.equals("!=")) {
      if (var2 != null && var2.trim.matches("NULL")) {
        try {
          if (!record(var1.trim.toInt).matches(EMPTY_VALUE_REGEX))
            valid = true
        } catch { case e: Exception => }
      } else if (var2 != null && var2.trim.matches("[0]+")) {
        try {
          if (!record(var1.trim.toInt).equals("0"))
            valid = true
        } catch { case e: Exception => }
      } else if (var2 != null && var2.trim.matches("[A-Za-z]")) {
        try {
          if (!record(var1.trim.toInt).trim.toUpperCase.equals(var2.trim.toUpperCase))
            valid = true
        } catch { case e: Exception => }
      } else {
        try { if (!record(var1.trim.toInt).trim.equals(record(var2.trim.toInt))) valid = true }
        catch {
          case e: Exception => logger.error("ERROR: An error occurred while doing conditional comparison.: "
            + record(var1.trim.toInt))
        }
      }
    }

    return (valid)
  }

  /**
   * Validates business types
   */
  private def isValidBusinessTypes(fileName: String, record: Array[String]): List[String] = {
    //logger.info("Business Type Validation ####### File Name: " + fileName + " \n#  Record: " + record.mkString(","))

    var errors = ListBuffer[String]()
    businessTypes.foreach { e =>
      try {
        val fieldRegex = e._1.split("\\.")
        val errorMsg = (errorMessages.get(fieldRegex(1).trim).getOrElse("").asInstanceOf[String]).split(":")
        val rule = props.getProperty(e._1.trim)

        e._2.foreach { f =>
          val fieldValue = record(f).trim.toUpperCase.replaceAll("[^A-Za-z0-9]", "")
          if (!fieldValue.isEmpty() && !fieldValue.matches(rule)) {
            errors.append(errorMessage(fileName, f.toString, record(f), errorMsg(1).trim, errorMsg(0).trim, errorMsg(2).trim))
          }
        }
      } catch { case e: Exception => }
    }

    return (errors.toList)
  }

  /**
   * Validate data types
   */
  private def isValidDataTypes(fileName: String, record: Array[String]): List[String] = {
    //logger.info("Data Type Validation ######## File Name: " + fileName + " \n# " + record.mkString(", "))

    var errors = ListBuffer[String]()
    dataTypes.foreach { e =>
      try {
        val fieldRegex = e._1.split("\\.")

        val errorMsg = (errorMessages.get(fieldRegex(1).trim).getOrElse("").asInstanceOf[String]).split(":")
        val rule = props.getProperty(e._1.trim)

        e._2.foreach { f =>
          var fieldValue = record(f).trim.toUpperCase
          if (!rule.contains("@")) fieldValue = fieldValue.replaceAll("[^A-Za-z0-9]", "")
          if (!fieldValue.isEmpty() && !fieldValue.matches(rule)) {
            errors.append(errorMessage(fileName, f.toString, record(f), errorMsg(1).trim, errorMsg(0).trim, errorMsg(2).trim))
          }
        }
      } catch { case e: Exception => }
    }
    return (errors.toList)
  }

  /**
   * Validates the number of fields in a record
   */
  private def isValidNumberOfColumns(fileName: String, record: Array[String]): List[String] = {
    //logger.info("Validating number of columns ######## File Name: " + fileName + " \n#" + record.mkString(", "))

    var errors = ListBuffer[String]()
    if (record != null && record.size < numCols) {
      val errorMsg = (errorMessages.get("numberOfColumns").getOrElse("").asInstanceOf[String]).split(":")
      errors.append(errorMessage(fileName, "", "", errorMsg(1).trim, errorMsg(0).trim, errorMsg(2).trim))
    }

    return (errors.toList)
  }

  private def isValidFileName(fileName: String, record: Array[String]): List[String] = {
    var errors = ListBuffer[String]()
    try {
      val submissionDate = ((fileName.split("\\."))(0)).substring(6, 14)
      val currentDate = dateFormat.format(new java.util.Date())
      if (submissionDate.toLong > currentDate.toLong) {
        val errorMsg = (errorMessages.get("submissionDateError").getOrElse("").asInstanceOf[String]).split(":")
        errors.append(errorMessage(fileName, "", "", errorMsg(1).trim, errorMsg(0).trim, errorMsg(2).trim))
      }
    } catch {
      case e: Exception =>
        val errorMsg = (errorMessages.get("submissionDateError").getOrElse("").asInstanceOf[String]).split(":")
        errors.append(errorMessage(fileName, "", "", errorMsg(1).trim, errorMsg(0).trim, errorMsg(2).trim))
    }

    return (errors.toList)
  }

  /**
   * Validates TU required fields
   * Returns a list. The list is empty if the record has values for all required fields
   *
   */
  private def isValidTURequired(fileName: String, record: Array[String]): List[String] = {
    //logger.info("Validating TU required fields ######## File Name: " + fileName + " \n#" + record.mkString(", "))

    var valid = true
    var errors = ListBuffer[String]()
    val errorMsg = (errorMessages.get("mandatory").getOrElse("").asInstanceOf[String]).split(":")
    tuRequiredI.foreach { f =>
      try {
        if (record(f).isEmpty || record(f).trim().matches(EMPTY_VALUE_REGEX)) {
          if (valid == true) valid = false
          errors.append(errorMessage(fileName, f.toString, record(f), errorMsg(1).trim, errorMsg(0).trim, errorMsg(2).trim))
        }
      } catch { case e: Exception => }
    }

    return (errors.toList)
  }

  /**
   * Validates KBA required fields
   * Returns a list. The list is empty if the record has values for all required fields
   *
   */
  private def isValidKBARequired(fileName: String, record: Array[String]): List[String] = {
    //logger.info("Validating KBA required fields ######## File Name: " + fileName + " \n#" + record.mkString(", "))

    var valid = true
    var errors = ListBuffer[String]()
    val errorMsg = (errorMessages.get("mandatory").getOrElse("").asInstanceOf[String]).split(":")
    kbaRequiredI.foreach { f =>
      try {
        if (record(f).isEmpty || record(f).trim().matches(EMPTY_VALUE_REGEX)) {
          if (valid == true) valid = false
          errors.append(errorMessage(fileName, f.toString, record(f), errorMsg(1).trim, errorMsg(0).trim, errorMsg(2).trim))
        }
      } catch { case e: Exception => }
    }
    return (errors.toList)
  }

  private def isValidIdentificationDocuments(fileName: String, record: Array[String]) = {

  }

  def isValid(fileName: String, record: Array[String], totalNumRecords: String): Boolean = {
    //logger.info("Validating: " + record.mkString(","))

    var tuValid = "1"
    var kbaValid = "1"
    var missingMandatory = "0"

    val errors = ListBuffer[String]()
    errors ++= isValidNumberOfColumns(fileName, record)
    errors ++= isValidFileName(fileName, record)

    if (errors.size == 0) {
      val validTURequired = isValidTURequired(fileName, record)
      val validKBARequired = isValidKBARequired(fileName, record)
      if (validTURequired.size > 0 || validKBARequired.size > 0) {
        missingMandatory = "1"
        tuValid = "0"
      }

      errors ++= validTURequired
      errors ++= validKBARequired
      errors ++= isValidDataTypes(fileName, record)
      errors ++= isValidBusinessTypes(fileName, record)
      errors ++= isValidConditional(fileName, record)
    }

    if (errors.size > 0) kbaValid = "0"
    val recordStr = record.map(_.trim).mkString("|")

    var recordMap: Map[String, Object] = Map()
    recordMap put ("fileName", fileName)
    recordMap put ("record", recordStr)
    recordMap put ("errors", errors.toList)
    recordMap put ("valid", tuValid)
    recordMap put ("kbaValid", kbaValid)
    recordMap put ("missingMandatory", missingMandatory)
    recordMap put ("accountNumber", getAccountNumber(fileType, record))
    recordMap.put("totalNumRecords", totalNumRecords)

    implicit val formats = DefaultFormats
    kafkaProducer.send(write(recordMap))

    if (tuValid == "1") true else false
  }
}