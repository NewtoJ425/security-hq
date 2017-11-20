package aws.iam

import aws.AWS
import aws.AwsAsyncHandler._
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.identitymanagement.model.{GenerateCredentialReportRequest, GenerateCredentialReportResult, GetCredentialReportRequest}
import com.amazonaws.services.identitymanagement.{AmazonIdentityManagementAsync, AmazonIdentityManagementAsyncClientBuilder}
import logic.Retry
import model.{AwsAccount, IAMCredentialsReport}
import utils.attempt.Attempt
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext


object IAMClient {
  def client(account: AwsAccount, region: Region = Region.getRegion(Regions.EU_WEST_1)): AmazonIdentityManagementAsync = {
    val auth = AWS.credentialsProvider(account)
    AmazonIdentityManagementAsyncClientBuilder.standard()
      .withCredentials(auth)
      .withRegion(Option(Regions.getCurrentRegion).getOrElse(region).getName).build()
  }

  def generateCredentialsReport(client: AmazonIdentityManagementAsync)(implicit ec: ExecutionContext): Attempt[GenerateCredentialReportResult] = {
    val request = new GenerateCredentialReportRequest()
    handleAWSErrs(awsToScala(client.generateCredentialReportAsync)(request))
  }

  private def getCredentialsReport(client: AmazonIdentityManagementAsync)(implicit ec: ExecutionContext): Attempt[IAMCredentialsReport] = {
    val request = new GetCredentialReportRequest()
    handleAWSErrs(awsToScala(client.getCredentialReportAsync)(request)).flatMap(CredentialsReport.extractReport)
  }

  def getCredentialsReport (account: AwsAccount)(implicit ec: ExecutionContext): Attempt[IAMCredentialsReport] = {
    val delay = 3.seconds
    val client = IAMClient.client(account)
    for {
      _ <- Retry.until(generateCredentialsReport(client), CredentialsReport.isComplete, "Failed to generate credentials report", delay)
      report <- getCredentialsReport(client)
    } yield report
  }

}
