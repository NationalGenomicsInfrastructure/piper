package molmed.qscripts

/*
 * Copyright (c) 2011, The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

import org.testng.annotations._
import molmed.queue.SnpSeqBaseTest
import org.broadinstitute.sting.queue.pipeline._

/**
 * TODO
 * Implement cluster style testing as in AlignWithBwaSnpSeqPipelineTest
 */

class VariantCallingSnpSeqPipelineTest {

    val pathToScript = "-S src/main/scala/molmed/qscripts/VariantCalling.scala"

    val snpSeqBaseTest = new SnpSeqBaseTest()

    var run: Boolean = false

    @BeforeClass
    @Parameters(Array("runpipeline"))
    def init(runpipeline: Boolean): Unit = {
        this.run = runpipeline
    }

    @Test
    def testBasicVariantCalling {
        val projectName = "test1"
        val testRawSNV = projectName + ".raw.snv.vcf"
        val testRawINDEL = projectName + ".raw.indel.vcf"
        val spec = new PipelineTestSpec
        spec.jobRunners = Seq("Shell")
        spec.name = "VariantCallingPipeline"
        spec.args = Array(
            pathToScript,
            " -R " + snpSeqBaseTest.fullHumanGenome,
            " -res " + "/non/exist/path",
            " -i " + snpSeqBaseTest.chromosome20Bam,
            " -intervals " + "/local/data/gatk_bundle/b37/first1000SNPsonChr20.intervals",
            " -outputDir " + "target/pipelinetests/VariantCallingPipeline/Shell/run/",
            " --nbr_of_threads 1 ",
            " --scatter_gather 1 ",
            " -noRecal ",
            " --test_mode ",
            " -startFromScratch ",
            " -p " + projectName).mkString
        spec.fileMD5s += testRawSNV -> "5699ed88a0d2d97dfcacd6860f13e568"
        spec.fileMD5s += testRawINDEL -> "25c219248b0f1b850803dda08f393a18"
        PipelineTest.executeTest(spec, run)
    }
}