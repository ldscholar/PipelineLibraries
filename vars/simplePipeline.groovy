//var/simplePipeline.groovy
//import static utils.PipelineStage.*

def call(String buildServer, List<String> deployServers, String remoteUrl, String credentialsId, Map<String, String> profile) {
    echo "call"
    /*
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
    */
}
