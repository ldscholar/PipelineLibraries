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
}

def isChanged(currentBuild) {
    def changeLogSets = currentBuild.changeSets
    if (null == changeLogSets || changeLogSets.isEmpty()) {
        return false
    } else {
        return true
    }
}

def build() {
    sh 'mvn clean deploy'
}

def deploy(String remoteRepositories, String workspace, String jarRunningPath, String profile) {
    def pom = readMavenPom file: 'pom.xml'
    def jar = pom.artifactId + '-' + pom.version + '.jar'
    def artifact = pom.parent.groupId + ':' + pom.artifactId + ':' + pom.version

    sh "mvn dependency:get -DremoteRepositories=$remoteRepositories -Dartifact=$artifact -Ddest=$workspace --quiet"
    try {
        sh "ps -ef | grep $jar | grep -v grep | awk '{print \$2}' | xargs kill -9"
    } catch (err) {
        echo "WARNING: 旧服务关闭失败,可能是旧服务未启动或已关闭"
    }

    dir("$jarRunningPath") {
        if (fileExists("$jar")) {
            sh "mv -b ./$jar ./backup/$jar"
        }
        sh "mv -f $workspace/$jar ./$jar"
        sh """JENKINS_NODE_COOKIE=dontKillMe
                        setsid java -jar $jar --spring.profiles.active=$profile &"""
    }
}

def call(String buildServer, String[] deployServers, String remoteUrl, String credentialsId, Map<String, String> profile) {
    node(buildServer) {
        stage('Checkout') {
            checkout(remoteUrl, credentialsId)
        }

        stage('Build') {
            if (isChanged(currentBuild)) {
                build()
                echo "构建成功."
            } else {
                echo "未检测到代码变化,不需要重新构建,已忽略."
            }
        }
    }

    if (deployServers.length > 0) {
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

