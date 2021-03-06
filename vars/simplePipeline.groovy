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

def isChanged(build) {
    def changeLogSets = build.changeSets
    if (null == changeLogSets || changeLogSets.isEmpty()) {
        return false
    } else {
        return true
    }
}

def build() {
    sh 'mvn clean deploy --quiet'
}

def deploy(String remoteRepositories, pom, String workspace, String jarRunningPath, String profile) {
    def jar = "${pom.artifactId}-${pom.version}.${pom.packaging}"
    def artifact
    if (null == pom.groupId) {
        artifact = "${pom.parent.groupId}:${pom.artifactId}:${pom.version}"
    } else {
        artifact = "${pom.groupId}:${pom.artifactId}:${pom.version}"
    }

    sh "mvn dependency:get -DremoteRepositories=$remoteRepositories -Dartifact=$artifact -Ddest=$workspace -Dpackaging=${pom.packaging} --quiet"
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

        if ("$profile".isEmpty()) {
            sh """JENKINS_NODE_COOKIE=dontKillMe
                    setsid java -jar $jar &"""
        } else {
            sh """JENKINS_NODE_COOKIE=dontKillMe
                    setsid java -jar $jar --spring.profiles.active=$profile &"""
        }
    }
}

def call(String buildServer, String[] deployServers, String remoteUrl, String credentialsId, boolean rebuild, Map<String, String> profile, String pomDir = './') {
    def pom
    node(buildServer) {
        stage('Checkout') {
            checkout(remoteUrl, credentialsId)
            dir(pomDir) {
                pom = readMavenPom file: 'pom.xml'
            }
        }

        stage('Build') {
            if (rebuild || isChanged(currentBuild)) {
                build()
            } else {
                echo "未检测到代码变化,不需要重新构建,已忽略Build步骤."
            }
        }
    }

    stage('Deploy') {
        if (deployServers.length > 0) {
            for (server in deployServers) {
                node(server) {
                    echo "deploying $server"
                    if (null == profile || profile.isEmpty() || null == profile[server]) {
                        deploy("$NEXUS", pom, "$WORKSPACE", "$JAR_RUNNING_PATH", "${env.SPRING_PROFILES_ACTIVE ?: ''}")
                    } else {
                        deploy("$NEXUS", pom, "$WORKSPACE", "$JAR_RUNNING_PATH", profile[server])
                    }
                }
            }
        } else {
            echo "没有指定服务器,不需要重新发布,已忽略Deploy步骤."
        }
    }
}

