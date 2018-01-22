//var/simplePipeline.groovy
import static sppl.utils.PipelineStage.*

def call(String buildServer, List<String> deployServers, String remoteUrl, String credentialsId, Map<String, String> profile) {
    node(buildServer) {
        stage('Checkout') {
            checkout(remoteUrl, credentialsId)
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
