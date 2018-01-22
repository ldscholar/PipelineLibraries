//var/simplePipeline.groovy
//import static utils.PipelineStage.*
def checkout(String remoteUrl, String credentialsId) {
    node("dss-12"){
        def scm = [$class              : 'SubversionSCM',
                   filterChangelog     : false,
                   ignoreDirPropChanges: false,
                   includedRegions     : '',
                   locations           : [[credentialsId: "$credentialsId", depthOption: 'infinity', ignoreExternalsOption: true, local: '.', remote: "$remoteUrl"]],
                   quietOperation      : true,
                   workspaceUpdater    : [$class: 'UpdateUpdater']]
        checkout(scm)
    }
}


def call(String buildServer, String remoteUrl, String credentialsId) {
    node(buildServer){
        echo "call"
    }
}


/*
def call(String buildServer, List<String> deployServers, String remoteUrl, String credentialsId, Map<String, String> profile) {
    node(buildServer){
        echo "call"
    }

    node(buildServer) {
        stage('Checkout') {
            checkout(remoteUrl, credentialsId)
        }

        stage('Build') {
            if (isChanged(currentBuild)) {
                build()
                echo "构建成功."
            } else {
                echo "代码未作修改,不需要重新构建,已忽略."
            }
        }
    }

    if (!deployServers.isEmpty()) {
        stage('Deploy') {
            for (server in deployServers) {
                node(server) {
                    echo "deploying $server"
                    deploy("$NEXUS", "$WORKSPACE", "$JAR_RUNNING_PATH", profile[server])
                }
            }
        }
    }
}
*/
