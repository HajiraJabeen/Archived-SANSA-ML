package net.sansa_stack.ml.spark.similarity.examples

import net.sansa_stack.ml.spark.similarity.similarity_measures.JaccardModel
import net.sansa_stack.ml.spark.utils.FeatureExtractorModel
import org.apache.jena.riot.Lang
import org.apache.spark.sql.{DataFrame, Row, SparkSession}
import net.sansa_stack.rdf.spark.io._
import org.apache.spark.ml.feature.{CountVectorizer, CountVectorizerModel, MinHashLSH, MinHashLSHModel}
import org.apache.spark.ml.linalg.Vector
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.functions.{col, udf}
import org.apache.spark.sql.types.DataTypes

object minimalCalls {
  def main(args: Array[String]): Unit = {

    // setup spark session
    val spark = SparkSession.builder
      .appName(s"MinMal Semantic Similarity Estimation Calls")
      .master("local[*]")
      .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .getOrCreate()

    // define inputpath if it is not parameter
    val inputPath = "/Users/carstendraschner/GitHub/SANSA-ML/sansa-ml-spark/src/main/resources/movie.nt"

    // read in data as Data`Frame
    val triplesDf: DataFrame = spark.read.rdf(Lang.NTRIPLES)(inputPath)

    triplesDf.show()

    // feature extraction
    val featureExtractorModel = new FeatureExtractorModel()
    val extractedFeaturesDataFrame = featureExtractorModel.transform(triplesDf)
    extractedFeaturesDataFrame.show()

    // count Vectorization
    val cvModel: CountVectorizerModel = new CountVectorizer()
      .setInputCol("extractedFeatures")
      .setOutputCol("vectorizedFeatures")
      .fit(extractedFeaturesDataFrame)
    val tmpCvDf: DataFrame = cvModel.transform(extractedFeaturesDataFrame)
    val isNoneZeroVector = udf({ v: Vector => v.numNonzeros > 0 }, DataTypes.BooleanType)
    val countVectorizedFeaturesDataFrame: DataFrame = tmpCvDf.filter(isNoneZeroVector(col("vectorizedFeatures"))).select("uri", "vectorizedFeatures")
    countVectorizedFeaturesDataFrame.show()

    // similarity estimations
    // for nearestNeighbors we need one key which is a Vector to search for NN
    val sample_key: Vector = countVectorizedFeaturesDataFrame.take(1)(0).getAs[Vector]("vectorizedFeatures")

    // minHash similarity estimation
    val mh: MinHashLSH = new MinHashLSH()
      // .setNumHashTables(parameterNumHashTables)
      .setInputCol("vectorizedFeatures")
      .setOutputCol("hashedFeatures")
    val minHashModel: MinHashLSHModel = mh.fit(countVectorizedFeaturesDataFrame)
    minHashModel.approxNearestNeighbors(countVectorizedFeaturesDataFrame, sample_key, 10, "minHashDistance").show()
    minHashModel.approxSimilarityJoin(countVectorizedFeaturesDataFrame, countVectorizedFeaturesDataFrame, 0.8, "distance").show()

    // Jaccard similarity
    val jaccardModel: JaccardModel = new JaccardModel()
      .setInputCol("vectorizedFeatures")
    // .sJ(countVectorizedFeaturesDataFrame, countVectorizedFeaturesDataFrame, 0.8).show()
    jaccardModel.nearestNeighbors(countVectorizedFeaturesDataFrame, sample_key, 10).show()
    jaccardModel.similarityJoin(countVectorizedFeaturesDataFrame, countVectorizedFeaturesDataFrame, threshold = 0.1).show()
  }
}
