import org.broadinstitute.sting.queue.extensions.gatk._
import org.broadinstitute.sting.queue.extensions.picard.PicardBamJarFunction
import org.broadinstitute.sting.queue.QScript
import org.broadinstitute.sting.queue.extensions.samtools.SamtoolsIndexFunction
import org.broadinstitute.sting.queue.function.ListWriterFunction
import scala.io.Source


class dataProcessing extends QScript {
  qscript =>

  @Input(doc="path to GATK jar", shortName="gatk", required=true)
  var GATKjar: File = _

  @Input(doc="path to AnalyzeCovariates.jar", shortName="ac", required=true)
  var ACJar: File = _

  // todo -- we should support the standard GATK arguments -R, -D [dbsnp],
  // todo -- and indel files.  Those should be defaulted to hg19 but providable on command line
  @Input(doc="path to R resources folder inside Sting", shortName="r", required=true)
  var R: String = _

  @Input(doc="path to Picard FixMateInformation.jar.  See http://picard.sourceforge.net/ .", shortName="fixmates", required=false)
  var fixMatesJar: File = new java.io.File("/seq/software/picard/current/bin/FixMateInformation.jar")

  @Input(doc="path to MarkDuplicates jar", shortName="dedup", required=false)
  var dedupJar: File = new java.io.File("/seq/software/picard/current/bin/MarkDuplicates.jar")

  @Input(doc="input BAM file", shortName="i", required=true)
  var input: String = _

  @Input(doc="final output BAM file base name", shortName="p", required=false)
  var projectName: String = "combined"

  @Input(doc="output path", shortName="outputDir", required=false)
  var outputDir: String = ""

  @Input(doc="the -L interval string to be used by GATK", shortName="L", required=false)
  var intervalString: String = ""

  // todo -- this shouldn't be allowed.  We want a flag that says "output bams at intervals only" or not
  @Input(doc="provide a .intervals file with the list of target intervals", shortName="intervals", required=false)
  var intervals: File = new File("/humgen/1kg/processing/pipeline_test_bams/whole_genome_chunked.hg19.intervals")


  // Reference sequence, dbsnps and RODs used by the pipeline
  val reference: File          = new File("/humgen/1kg/reference/human_g1k_v37.fasta")
  val dbSNP: File              = new File("/humgen/gsa-hpprojects/GATK/data/dbsnp_132_b37.leftAligned.vcf")

  // TODO -- let's create a pre-merged single VCF and put it into /humgen/gsa-hpprojects/GATK/data please
  val dindelPilotCalls: String = "/humgen/gsa-hpprojects/GATK/data/Comparisons/Unvalidated/1kg.pilot_release.merged.indels.sites.hg19.vcf"
  val dindelAFRCalls: String   = "/humgen/1kg/DCC/ftp/technical/working/20110126_dindel_august/AFR.dindel_august_release_merged_pilot1.20110126.sites.vcf.gz"
  val dindelASNCalls: String   = "/humgen/1kg/DCC/ftp/technical/working/20110126_dindel_august/ASN.dindel_august_release_merged_pilot1.20110126.sites.vcf.gz"
  val dindelEURCalls: String   = "/humgen/1kg/DCC/ftp/technical/working/20110126_dindel_august/EUR.dindel_august_release_merged_pilot1.20110126.sites.vcf.gz"

  // Simple boolean definitions for code clarity
  val knownsOnly: Boolean = true
  val intermediate: Boolean = true

  // General arguments to all programs
  trait CommandLineGATKArgs extends CommandLineGATK {
    this.jarFile = qscript.GATKjar
    this.reference_sequence = reference
    this.memoryLimit = Some(4)
    this.isIntermediate = true
  }


  def script = {

    var perLaneBamList: List[String] = Nil
    var recalibratedBamList: List[File] = Nil
    var recalibratedBamIndexList: List[File] = Nil

    // Populates the list of per lane bam files to process (single bam or list of bams).
    if (input.endsWith("bam"))  {
      perLaneBamList :+= input
    }
    else {
      for (bam <- Source.fromFile(input).getLines())
        perLaneBamList :+= bam
    }



    perLaneBamList.foreach { perLaneBam =>

      // Helpful variables
      val baseName: String        = swapExt(new File(perLaneBam.substring(perLaneBam.lastIndexOf("/")+1)), ".bam", "").toString()
      val baseDir: String         = perLaneBam.substring(0, perLaneBam.lastIndexOf("/")+1)

      // BAM files generated by the pipeline
      val cleanedBam: String      = baseName + ".cleaned.bam"
      val fixedBam: String        = baseName + ".cleaned.fixed.bam"
      val dedupedBam: String      = baseName + ".cleaned.fixed.dedup.bam"
      val recalBam: String        = baseName + ".cleaned.fixed.dedup.recal.bam"

      // Accessory files
      val targetIntervals: String = baseName + ".indel.intervals"
      val metricsFile: String     = baseName + ".metrics"
      val preRecalFile: String    = baseName + ".pre_recal.csv"
      val postRecalFile: String   = baseName + ".post_recal.csv"
      val preOutPath: String      = baseName + ".pre"
      val postOutPath: String     = baseName + ".post"

      add(new target(perLaneBam, targetIntervals),
          new clean(perLaneBam, targetIntervals, cleanedBam, knownsOnly), // todo -- use constrained movement mode to skip this
          new fixMates(cleanedBam, fixedBam, intermediate), // todo -- use constrained movement mode to skip this
          new dedup(fixedBam, dedupedBam, metricsFile),     // todo -- generate index on fly here
          new index(dedupedBam), // todo -- remove for on the fly index
          new cov(dedupedBam, preRecalFile),
          new recal(dedupedBam, preRecalFile, recalBam), // todo -- use GATK on the fly indexing?
          new index(recalBam), // todo remove for on the fly indexing
          new cov(recalBam, postRecalFile),
          new analyzeCovariates(preRecalFile, preOutPath),
          new analyzeCovariates(postRecalFile, postOutPath))

      recalibratedBamList :+= new File(recalBam)
      recalibratedBamIndexList :+= new File(recalBam + ".bai")  // to hold next process by this dependency
    }

    // Helpful variables
    val outName: String        = qscript.projectName
    val outDir: String         = qscript.outputDir

    // BAM files generated by the pipeline
    val bamList: String         = outDir + outName + ".list"
    val cleanedBam: String      = outDir + outName + ".cleaned.bam"
    val fixedBam: String        = outDir + outName + ".final.bam"

    // Accessory files
    val targetIntervals: String = outDir + outName + ".indel.intervals"

    add(new writeList(recalibratedBamList, bamList, recalibratedBamIndexList),
        new target(bamList, targetIntervals),
        new clean(bamList, targetIntervals, cleanedBam, !knownsOnly), // todo -- use constrained movement mode to skip fix mates
        new fixMates(cleanedBam, fixedBam, !intermediate)) // todo -- use constrained movement mode to skip this
  }

  class target (inBams: String, outIntervals: String) extends RealignerTargetCreator with CommandLineGATKArgs {
      this.input_file :+= new File(inBams)
      this.out = new File(outIntervals)
      this.mismatchFraction = Some(0.0)
      this.rodBind :+= RodBind("dbsnp", "VCF", dbSNP)
      this.rodBind :+= RodBind("indels1", "VCF", dindelPilotCalls)
      this.rodBind :+= RodBind("indels2", "VCF", dindelAFRCalls)
      this.rodBind :+= RodBind("indels3", "VCF", dindelEURCalls)
      this.rodBind :+= RodBind("indels4", "VCF", dindelASNCalls)
      this.jobName = inBams + ".tgt"
      if (!qscript.intervalString.isEmpty()) this.intervalsString :+= qscript.intervalString
      else this.intervals :+= qscript.intervals
  }

  class clean (inBams: String, tIntervals: String, outBam: String, knownsOnly: Boolean) extends IndelRealigner with CommandLineGATKArgs {
    this.input_file :+= new File(inBams)
    this.targetIntervals = new File(tIntervals)
    this.out = new File(outBam)
    this.doNotUseSW = true
    this.baq = Some(org.broadinstitute.sting.utils.baq.BAQ.CalculationMode.CALCULATE_AS_NECESSARY)
    this.rodBind :+= RodBind("dbsnp", "VCF", dbSNP)
    this.rodBind :+= RodBind("indels1", "VCF", dindelPilotCalls)
    this.rodBind :+= RodBind("indels2", "VCF", dindelAFRCalls)
    this.rodBind :+= RodBind("indels3", "VCF", dindelEURCalls)
    this.rodBind :+= RodBind("indels4", "VCF", dindelASNCalls)
    this.useOnlyKnownIndels = knownsOnly
    this.sortInCoordinateOrderEvenThoughItIsHighlyUnsafe = true
    this.jobName = inBams + ".clean"
    if (!qscript.intervalString.isEmpty()) this.intervalsString ++= List(qscript.intervalString)
    else this.intervals :+= qscript.intervals
  }

  class fixMates (inBam: String, outBam: String, intermediate: Boolean) extends PicardBamJarFunction {
    @Input(doc="cleaned bam") var cleaned: File = new File(inBam)
    @Output(doc="fixed bam") var fixed: File = new File(outBam)
    override def inputBams = List(cleaned)
    override def outputBam = fixed
    this.jarFile = qscript.fixMatesJar
    this.isIntermediate = intermediate
    this.memoryLimit = Some(6)
    this.jobName = inBam + ".fix"
  }

  class dedup (inBam: String, outBam: String, metricsFile: String) extends PicardBamJarFunction {
    @Input(doc="fixed bam") var clean: File = new File(inBam)
    @Output(doc="deduped bam") var deduped: File = new File(outBam)
    override def inputBams = List(clean)
    override def outputBam = deduped
    override def commandLine = super.commandLine + " M=" + metricsFile
    sortOrder = null
    this.memoryLimit = Some(6)
    this.jarFile = qscript.dedupJar
    this.jobName = inBam + ".dedup"
  }

  // todo -- may we should use the picard version instead?  What about telling all of the picard tools to
  // todo -- generate BAM indices on the fly?  That would be even better
  class index (inBam: String) extends SamtoolsIndexFunction {
    @Output(doc="bam index file") var outIndex: File = new File(inBam + ".bai")
    this.bamFile = new File(inBam)
    this.analysisName = inBam + ".index"
  }

  class cov (inBam: String, outRecalFile: String) extends CountCovariates with CommandLineGATKArgs {
    this.rodBind :+= RodBind("dbsnp", "VCF", dbSNP)
    this.covariate ++= List("ReadGroupCovariate", "QualityScoreCovariate", "CycleCovariate", "DinucCovariate")
    this.input_file :+= new File(inBam)
    this.recal_file = new File(outRecalFile)
  }

  class recal (inBam: String, inRecalFile: String, outBam: String) extends TableRecalibration with CommandLineGATKArgs {
    this.input_file :+= new File (inBam)
    this.recal_file = new File(inRecalFile)
    this.out = new File(outBam)
  }

  class analyzeCovariates (inRecalFile: String, outPath: String) extends AnalyzeCovariates {
    this.jarFile = qscript.ACJar
    this.resources = qscript.R
    this.recal_file = new File(inRecalFile)
    this.output_dir = outPath
  }

  class writeList(inBams: List[File], outBamList: String, depIndices: List[File]) extends ListWriterFunction {
    @Input(doc="bam indexes") var indexes: List[File] = depIndices   // I need this dependency to hold creation of the list until all indices are done
    this.inputFiles = inBams
    this.listFile = new File(outBamList)
    this.jobName = "bamList"
  }
}