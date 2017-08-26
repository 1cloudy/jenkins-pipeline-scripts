package jobs.scripts;

class Utilities {

    private static String DefaultSharedLibName = 'virto-shared-library'

    /**
     * Get the folder name for a job.
     *
     * @param project Project name (e.g. dotnet/coreclr)
     * @return Folder name for the project. Typically project name with / turned to _
     */
    def static getFolderName(String project) {
        return project.replace('/', '_')
    }

    def static runSharedPS(context, scriptName)
    {
    	def wsFolder = context.pwd()
 	    context.bat "powershell.exe -File \"${wsFolder}\\..\\workspace@libs\\${DefaultSharedLibName}\\${scriptName}\""
    }

    def static getAssemblyVersion(context)
    {
        context.echo "Searching for version inside CommonAssemblyInfo.cs file"
        def matcher = context.readFile('CommonAssemblyInfo.cs') =~ /AssemblyFileVersion\(\"(\d+\.\d+\.\d+)/
        def version = matcher[0][1]
        context.echo "Found version ${version}"
        return version
    }

    def static getTestDlls(context)
    {
        def testDlls = context.findFiles(glob: '**\\bin\\Debug\\*Test.dll')
        return testDlls
    }

    def static getComposeFolder(context)
    {
        def wsFolder = context.pwd()
        def composeDir = "$wsFolder\\..\\workspace@libs\\${DefaultSharedLibName}\\resources\\docker"
        return composeDir
    }    

    def static getArtifactFolder(context)
    {
        def wsFolder = context.pwd()
        def packagesDir = "$wsFolder\\artifacts"
        return packagesDir
    }

    def static getWebPublishFolder(context, String websiteDir)
    {
        def tempFolder = Utilities.getTempFolder(context)
        def websitePath = "$tempFolder\\_PublishedWebsites\\${websiteDir}"
        return websitePath
    }

    def static getTempFolder(context)
    {
        def tempFolder = context.pwd(tmp: true)
        return tempFolder
    }

    def static notifyBuildStatus(context, status)
    {
        context.office365ConnectorSend status:context.currentBuild.result, webhookUrl:context.env.O365_WEBHOOK
    }    

    def static getActiveBuildOrder(context)
    {
        def instance = Jenkins.getInstance()
        def globalNodeProperties = instance.getGlobalNodeProperties()
        def envVarsNodePropertyList = globalNodeProperties.getAll(hudson.slaves.EnvironmentVariablesNodeProperty.class)

        def newEnvVarsNodeProperty = null
        def envVars = null

        if ( envVarsNodePropertyList == null || envVarsNodePropertyList.size() == 0 ) {
            newEnvVarsNodeProperty = new hudson.slaves.EnvironmentVariablesNodeProperty();
            globalNodeProperties.add(newEnvVarsNodeProperty)
            envVars = newEnvVarsNodeProperty.getEnvVars()
        } else {
            envVars = envVarsNodePropertyList.get(0).getEnvVars()
        }

        def currentOrder = envVars.get("VC_BUILD_ORDER")       

        if(currentOrder) // exists
        {
            currentOrder = currentOrder + 1
            
            if(currentOrder >= 10) // reset, we can't have more than 10 builders at the same time
            {
                currentOrder = 0
            }
        }
        else
        {
            currentOrder = 0
        }

        envVars.put("VC_BUILD_ORDER", currentOrder.toString())
        instance.save()

        return currentOrder
    }

    Object withDockerCredentials(Closure body) {
        withCredentials([[$class: 'ZipFileBinding', credentialsId: 'docker-hub-credentials', variable: 'DOCKER_CONFIG']]) {
            return body.call()
        }
    }    
}