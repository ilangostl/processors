package edu.arizona.sista.learning

import org.scalatest.junit.AssertionsForJUnit
import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import java.io.{File, PrintWriter}
import junit.framework.Assert
import org.junit.Test
import scala.sys.process._

/**
 *
 * User: mihais
 * Date: 4/25/13
 */
class TestSVMRankingClassifier  extends AssertionsForJUnit {

  @Test def testClassifier() {
    val dataset = new BVFRankingDataset[String]

    val qid1 = new ListBuffer[Datum[Int, String]]
    qid1 += new BVFDatum[Int, String](2, List("good", "great"))
    qid1 += new BVFDatum[Int, String](1, List("bad", "awful"))
    dataset += qid1.toList

    val qid2 = new ListBuffer[Datum[Int, String]]
    qid2 += new BVFDatum[Int, String](2, List("good"))
    qid2 += new BVFDatum[Int, String](1, List("great"))
    dataset += qid2.toList

    println("Dataset feature lexicon is:\n" + dataset.featureLexicon)
    // normally, keepIntermediateFiles = false
    // also, set the working directory to something meaningful in your application
    val classifier = new SVMRankingClassifier[String](".", keepIntermediateFiles = true)
    classifier.train(dataset)

    val qid3 = new ListBuffer[Datum[Int, String]]
    qid3 += new BVFDatum[Int, String](2, List("good", "bad"))
    qid3 += new BVFDatum[Int, String](1, List("great"))
    val scores = classifier.scoresOf(qid3)
    println("Scores: " + scores)

    //
    // the code before is just for debug purposes
    // we do not do this in real life
    //

    // let's make sure we get the same values as svm_rank_classify
    val pw = new PrintWriter("./test.dat")
    classifier.mkTestFile(pw, qid3, 1)
    pw.close()
    val exitCode = "svm_rank_classify ./test.dat ./model.dat ./predictions".!
    Assert.assertTrue(exitCode == 0)

    val svmRankClassifyScores =
      io.Source.fromFile("./predictions").getLines().map(l => l.toDouble).toArray
    val ourScores = scores.toArray
    Assert.assertTrue(svmRankClassifyScores(0) == ourScores(0))
    Assert.assertTrue(svmRankClassifyScores(1) == ourScores(1))
    println("Scores match!")

    //
    // let's verify that we get the same scores after serialization
    //
    classifier.saveTo("./model.ser")
    val serCls:SVMRankingClassifier[String] = SVMRankingClassifier.loadFrom("./model.ser")
    val serScores = serCls.scoresOf(qid3).toArray
    Assert.assertTrue(serScores(0) == ourScores(0))
    Assert.assertTrue(serScores(1) == ourScores(1))
    println("Scores after serialization match!")

    // delete all files generated here
    new File("./model.dat").delete()
    new File("./train.dat").delete()
    new File("./test.dat").delete()
    new File("./predictions").delete()
    new File("./model.ser").delete()

  }

  @Test def testClassifier2() {
    val dataset = RVFRankingDataset.mkDatasetFromSvmRankFormat(
      "src/test/resources/edu/arizona/sista/learning/ranking_train.txt.gz")

    val classifier = new SVMRankingClassifier[String](".", keepIntermediateFiles = true)
    classifier.train(dataset)

    val queries = RVFRankingDataset.mkDatumsFromSvmRankFormat(
      "src/test/resources/edu/arizona/sista/learning/ranking_test.txt.gz")
    val queryScores = new ArrayBuffer[Array[Double]]()
    for(query <- queries) {
      val scores = classifier.scoresOf(query)
      queryScores += scores.toArray
    }

    // we should do better the 50% P@1 on this dataset
    val p = RankerEvaluator.score(queries, queryScores.toArray)
    println("P@1 = " + p)
    Assert.assertTrue(p >= 0.50)

    // delete all files generated here
    new File("./model.dat").delete()
    new File("./train.dat").delete()
  }
}
