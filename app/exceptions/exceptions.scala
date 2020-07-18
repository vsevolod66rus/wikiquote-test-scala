package exceptions {
  final case class InvalidInputException(private val message: String)
      extends Exception(message)
}
