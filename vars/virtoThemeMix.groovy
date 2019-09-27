#!groovy
import jobs.scripts.*

// module script
def call(body) {
	// evaluate the body block, and collect configuration into the object
	def config = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = config
	body()
    
	node {
		properties([disableConcurrentBuilds()])
	    def storeName = config.sampleStore
		projectType = config.projectType
		if(projectType==null){
			projectType = 'Theme'
		}
		try {
			echo "Building branch ${env.BRANCH_NAME}"
			Utilities.notifyBuildStatus(this, "Started")

			stage('Checkout') {
				timestamps { 
					deleteDir()
					checkout scm
				}
			}

			stage('Code Analysis'){
				timestamps{
					bat "npm install -g typescript"
					bat "npm install typescript"
					echo "Packaging.startSonarJS"
        			def sqScannerMsBuildHome = tool 'Scanner for MSBuild'
        			def fullJobName = Utilities.getRepoName(this)

					withSonarQubeEnv('VC Sonar Server') {
						bat "\"${sqScannerMsBuildHome}\\sonar-scanner-3.0.3.778\\bin\\sonar-scanner.bat\" scan -Dsonar.projectKey=theme_default_${env.BRANCH_NAME} -Dsonar.sources=. -Dsonar.branch=${env.BRANCH_NAME} -Dsonar.projectName=\"${fullJobName}\" -Dsonar.host.url=%SONAR_HOST_URL% -Dsonar.login=%SONAR_AUTH_TOKEN%"
					}
				}
			}

			stage('Build')
			{
				timestamps
				{
					if (env.BRANCH_NAME == 'dev')
					{
						dir("${env.WORKSPACE}\\ng-app")
						{
							bat "npm install --production=\"false\" --prefer-offline"
							bat "npm run build"
						}
					}
					else
					{
						dir("${env.WORKSPACE}\\ng-app")
						{
							bat "npm install --prefer-offline"
							bat "npm run build"
						}
					}
				}
			}

			stage('Quality Gate')
			{
				timestamps
				{
					withSonarQubeEnv("VC Sonar Server"){
						dir("${env.WORKSPACE}\\ng-app")
						{
							Packaging.checkAnalyzerGate(this)
						}
					}
                }
            }

			def version
			dir("${env.WORKSPACE}\\ng-app")
			{
				version = Utilities.getPackageVersion(this)
			}
			try {
				bat "rmdir .git .vs .vscode .scannerwork node_modules ng-app@tmp ng-app\\node_modules /s /q"
				bat "del .deployment .gitignore Jenkinsfile package-lock.json deploy.cmd /s /q"
			}
			catch (any) {}
			def zipFile = "${env.WORKSPACE}\\artifacts\\dental-theme-${version}.zip"

			stage('Packaging')
			{
				timestamps {
					zip zipFile: zipFile, dir: "./"
					if(params.themeResultZip != null) {
						bat "copy /Y \"${zipFile}\" \"${params.themeResultZip}\""
					}
				}
			}

			if(params.themeResultZip == null)
			{
				stage('Publish')
				{
					timestamps
					{
						if (Packaging.getShouldPublish(this))
						{
							Packaging.publishRelease(this, version, "")
						}
						if (env.BRANCH_NAME == 'dev' || 'master')
						{
							def stagingName = Utilities.getStagingNameFromBranchName(this)
							Utilities.runSharedPS(this, "VC-ThemeMix2Azure.ps1", /-StagingName "${stagingName}" -StoreName "${storeName}"/)
						}
					}
				}
			}
		}
		catch (any) {
			currentBuild.result = 'FAILURE'
			Utilities.notifyBuildStatus(this, currentBuild.result)
			throw any //rethrow exception to prevent the build from proceeding
		}
		finally {
			step([$class: 'LogParserPublisher',
				  failBuildOnError: false,
				  parsingRulesPath: env.LOG_PARSER_RULES,
				  useProjectRule: false])
			if(currentBuild.result != 'FAILURE') {
				step([$class: 'Mailer', notifyEveryUnstableBuild: true, recipients: emailextrecipients([[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']])])
			}
			else {
				def log = currentBuild.rawBuild.getLog(300)
				def failedStageLog = Utilities.getFailedStageStr(log)
				def failedStageName = Utilities.getFailedStageName(failedStageLog)
				def mailBody = Utilities.getMailBody(this, failedStageName, failedStageLog)
				emailext body:mailBody, subject: "${env.JOB_NAME}:${env.BUILD_NUMBER} - ${currentBuild.currentResult}", recipientProviders: [[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']]
			}
		}
	
	  	step([$class: 'GitHubCommitStatusSetter', statusResultSource: [$class: 'ConditionalStatusResultSource', results: []]])
		Utilities.notifyBuildStatus(this, currentBuild.result)
	}
}
