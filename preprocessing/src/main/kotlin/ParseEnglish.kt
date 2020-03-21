import edu.stanford.nlp.simple.Sentence

fun main() {
  val sent = Sentence("The advantages to using this container over a Map<K, Collection<V>> is that all of the handling of the value collection can be done automatically. It also allows implementations to further specialize in how duplicate values will be handled. Value collections with list semantics would allow duplicate values for a key, while those implementing set semantics would not.");
  val nerTags = sent.nerTags();  // [PERSON, O, O, O, O, O, O, O]
  val firstPOSTag = sent.posTag(0);   // NNP
  println(sent.dependencyGraph().toDotFormat())
}