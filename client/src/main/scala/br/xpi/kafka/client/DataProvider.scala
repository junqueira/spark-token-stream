package br.xpi.kafka.client

import java.io.{ByteArrayOutputStream, File}
import java.nio.file.{Files, Paths}
import com.google.protobuf.ByteString
import scala.concurrent.Future
import scala.io.Source
import scala.concurrent.ExecutionContext.Implicits.global

import br.xpi.configuration.kafka.ApplicationKafkaParameters._
import br.xpi.model.modeldescriptor.ModelDescriptor
import br.xpi.model.winerecord.WineRecord
import br.xpi.kafka.{KafkaLocalServer, MessageSender}
import br.xpi.model.tokenrecord.TokenRecord


object DataProvider {

    val file = "test/data/winequality_red.csv"
    var dataTimeInterval = 1000 * 1 // 1 sec
    val directory = "test/data/"
    val tensorfile = "test/data/optimized_WineQuality.pb"
    var modelTimeInterval = 1000 * 60 * 1 // 5 mins

    def main(args: Array[String]) {

        println(s"Using kafka brokers at ${KAFKA_BROKER}")
        println(s"Data Message delay $dataTimeInterval")
        println(s"Model Message delay $modelTimeInterval")

        val kafka = KafkaLocalServer(true)
        println(s"Cluster created...")
        kafka.start()
        kafka.createTopic(DATA_TOPIC)
        kafka.createTopic(MODELS_TOPIC)

        publishData()
        publishModels()

        while(true)
            pause(600000)
    }

    def publishData() :  Future[Unit] = Future {
        val sender = MessageSender(KAFKA_BROKER)
        val bos = new ByteArrayOutputStream()
//        val file = "test/data/data_test.txt"
//        val records = getListOfDataRecordsToken(file)
        val file = "test/data/winequality_red.csv"
        val records = getListOfDataRecords(file)
        println(s"printed file test $records ")

        var nrec = 0
        while (true) {
            records.foreach(r => {
                bos.reset()
                r.writeTo(bos)
                sender.writeValue(DATA_TOPIC, bos.toByteArray)
                nrec = nrec + 1
                if (nrec % 10 == 0)
                    println(s"printed $nrec records")
                pause(dataTimeInterval)
            })
        }
    }

    def publishModels() : Future[Unit] = Future {
        val sender = MessageSender(KAFKA_BROKER)
        val files = getListOfModelFiles(directory)
        val bos = new ByteArrayOutputStream()
        while (true) {
            files.foreach(f => {
                // PMML
                val pByteArray = Files.readAllBytes(Paths.get(directory + f))
                val pRecord = ModelDescriptor(
                    name = f.dropRight(5),
                    description = "generated tokenization", modeltype = ModelDescriptor.ModelType.PMML,
                    dataType = "wine"
                ).withData(ByteString.copyFrom(pByteArray))
                bos.reset()
                pRecord.writeTo(bos)
                sender.writeValue(MODELS_TOPIC, bos.toByteArray)
                println(s"Published _bulk ${pRecord.description}")
                pause(modelTimeInterval)
            })
            // TF
            val tByteArray = Files.readAllBytes(Paths.get(tensorfile))
            val tRecord = ModelDescriptor(name = tensorfile.dropRight(3),
                description = "generated from TensorFlow", modeltype = ModelDescriptor.ModelType.TENSORFLOW,
                dataType = "wine").withData(ByteString.copyFrom(tByteArray))
            bos.reset()
            tRecord.writeTo(bos)
            sender.writeValue(MODELS_TOPIC, bos.toByteArray)
            println(s"Published _bulk ${tRecord.description}")
            pause(modelTimeInterval)
        }
    }

    private def pause(timeInterval : Long): Unit = {
        try {
            Thread.sleep(timeInterval)
        } catch {
            case _: Throwable => // Ignore
        }
    }

    def getListOfDataRecordsToken(file: String): Seq[TokenRecord] = {
        var result = Seq.empty[TokenRecord]
        val bufferedSource = Source.fromFile(file)
        for (line <- bufferedSource.getLines) {
            val cols = line.split(",").map(_.trim)
            val record = new TokenRecord(
                collum = cols(0),
                hash = cols(1),
                value = cols(2)
//                dataType = "token"
            )
            println(s"printed $record ")
            result = record +: result
        }
        bufferedSource.close
        result
    }

    def getListOfDataRecords(file: String): Seq[WineRecord] = {
        var result = Seq.empty[WineRecord]
        val bufferedSource = Source.fromFile(file)
        for (line <- bufferedSource.getLines) {
            val cols = line.split(";").map(_.trim)
            val record = new WineRecord(
                fixedAcidity = cols(0).toDouble,
                volatileAcidity = cols(1).toDouble,
                citricAcid = cols(2).toDouble,
                residualSugar = cols(3).toDouble,
                chlorides = cols(4).toDouble,
                freeSulfurDioxide = cols(5).toDouble,
                totalSulfurDioxide = cols(6).toDouble,
                density = cols(7).toDouble,
                pH = cols(8).toDouble,
                sulphates = cols(9).toDouble,
                alcohol = cols(10).toDouble,
                dataType = "wine"
            )
            result = record +: result
        }
        bufferedSource.close
        result
    }

    private def getListOfModelFiles(dir: String): Seq[String] = {
        val d = new File(dir)
        if (d.exists && d.isDirectory) {
            d.listFiles.filter(f => (f.isFile) && (f.getName.endsWith(".pmml"))).map(_.getName)
        } else {
            Seq.empty[String]
        }
    }
}
