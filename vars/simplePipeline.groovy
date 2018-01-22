//var/simplePipeline.groovy
def checkout(String remoteUrl, String credentialsId) {
    def scm = [$class              : 'SubversionSCM',
               filterChangelog     : false,
               ignoreDirPropChanges: false,
               includedRegions     : '',
               locations           : [[credentialsId: "$credentialsId", depthOption: 'infinity', ignoreExternalsOption: true, local: '.', remote: "$remoteUrl"]],
               quietOperation      : true,
               workspaceUpdater    : [$class: 'UpdateUpdater']]
    checkout(scm)

    def changeLogSets = currentBuild.changeSets
    if (null == changeLogSets || changeLogSets.isEmpty()) {
        env.isChanged = 'false'
    } else {
        env.isChanged = 'true'
    }
}

def call(String buildServer, String remoteUrl, String credentialsId){
    node(buildServer) {
        stage('Checkout') {
            checkout(remoteUrl, credentialsId)
        }

        stage('Build') {
            //build()
        }
    }
}

/*
def call(String buildServer, List<String> deployServers, String remoteUrl, String credentialsId, Map<String, String> profile){
    pipeline{
        node(buildServer) {
            stage('Checkout') {
                checkout(credentialsId, remoteUrl)
            }

            stage('Build') {
                build()
            }
        }

        if (!deployServers.isEmpty()) {
            stage('Deploy') {
                for (server in deployServers) {
                    node(server) {
                        echo "deploying $server"
                        deploy(profile[server])
                    }
                }
            }
        }
    }
}
*/