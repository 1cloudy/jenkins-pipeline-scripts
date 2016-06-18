// module script
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    
    // you can call any valid step functions from your code, just like you can from Pipeline scripts
    echo "Building ${config.name} with branch ${env.BRANCH_NAME}, job: ${env.JOB_NAME}"
    
    // github-organization-plugin jobs are named as 'org/repo/branch'
    tokens = "${env.JOB_NAME}".tokenize('/')
    org = tokens[0]
    repo = tokens[1]
    branch = tokens[2]

    def solution = "${config.solution}"

	env.WORKSPACE = pwd()
	stage 'Checkout'
		//checkout scm
		//checkout([$class: 'GitSCM', branches: [[name: "*/${env.BRANCH_NAME}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: ''], [$class: 'CheckoutOption']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'sasha-jenkins', url: "git@github.com:VirtoCommerce/${repo}.git"]]])
		//checkout([$class: 'GitSCM', extensions: [[$class: 'PathRestriction', excludedRegions: 'CommonAssemblyInfo\\.cs']]])
		checkout poll:true, scm: [
			$class: 'GitSCM', 
			extensions: [[
				$class: 'PathRestriction', 
				excludedRegions: 'CommonAssemblyInfo\\.cs', 
				includedRegions: ''
			]], 
			userRemoteConfigs: [[
				credentialsId: 'sasha-jenkins', url: "git@github.com:VirtoCommerce/${repo}.git"
			]]
		]
		
		def solutions = findFiles(glob: '*.sln')

		stage 'Build'
			solutions.each {
				bat "Nuget restore ${it.name}"
				bat "\"${tool 'MSBuild 12.0'}\" \"${it.name}\" /p:Configuration=Debug /p:Platform=\"Any CPU\""
			}

	if (env.BRANCH_NAME == 'master') {
				
		stage 'Publish'
		    	updateVersion(env.WORKSPACE)
	   		bat 'Nuget\\build.bat'
	} 
	
	step([$class: 'GitHubCommitStatusSetter', statusResultSource: [$class: 'ConditionalStatusResultSource', results: []]])
}

def updateVersion(workspace)
{
    bat "powershell.exe -File \"${env.JENKINS_HOME}\\workflow-libs\\vars\\version.ps1\" -solutiondir \"${workspace}\""
    
    bat "\"${tool 'Git'}\" config user.email \"ci@virtocommerce.com\""
    bat "\"${tool 'Git'}\" config user.name \"Virto Jenkins\""
    bat "\"${tool 'Git'}\" commit -am \"Updated version number ${env.BUILD_TAG}\""
    bat "\"${tool 'Git'}\" push origin HEAD:master -f"
}
