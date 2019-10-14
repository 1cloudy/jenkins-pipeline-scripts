node {
    def SETTINGS
    def settingsFileContent
    configFileProvider([configFile(fileId: 'shared_lib_settings', variable: 'SETTINGS_FILE')]) {
        settingsFileContent = readFile(SETTINGS_FILE)
    }
    SETTINGS = new Settings(settingsFileContent)
    SETTINGS.setRegion('platform-core')
    SETTINGS.setEnvironment(env.BRANCH_NAME)
    stage('Checkout'){
        deleteDir()
        checkout scm
    }

    stage('Build'){
        bat "vc-build Compress"
        bat "vc-build Pack"
    }

    stage('Unit Tests'){
        bat "vc-build Test"
    }   

    stage('Deploy'){
        // $ZipFile,
        // $WebAppName,
        // $ResourceGroupName,
        // $SubscriptionID,
        // $DestContentPath = ""
        def artifacts = glob "artifacts/*.zip"
        def artifactPath = artifacts[0].path
        Utilities.runSharedPS(this, "DeployTo-Azure.ps1", "-ZipFile \"${artifactPath}\" -WebAppName ${SETTINGS['webAppName']} -ResourceGroupName ${SETTINGS['resourceGroupName']} -SubscriptionID ${SETTINGS['subscriptionID']} -DestContentPath \"platform\"")
    }
}