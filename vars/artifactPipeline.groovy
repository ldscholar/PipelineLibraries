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

def build(String targetDir, String jarSavePath, String jarName) {
    sh 'mvn clean package --quiet'
    //打完包之后,复制jar包到$jarRunningPath("/home/ways")下面
    dir("$jarSavePath") {
        if (fileExists("$jarName")) {
            sh "mv -b ./$jarName ./backup/$jarName"
        }

        sh "mv -f $WORKSPACE/$targetDir/$jarName ./$jarName"
    }
}

def deploy(boolean rebuild, String jarName, String workspace, String jarRunningPath, String profile) {
    try {
        sh "ps -ef | grep $jarName | grep -v grep | awk '{print \$2}' | xargs kill -9"
    } catch (err) {
        echo "WARNING: 旧服务关闭失败,可能是旧服务未启动或已关闭"
    }

    dir("$jarRunningPath") {
        if (fileExists("$jarName")) {
            sh "mv -b ./$jarName ./backup/$jarName"
        }

        unstash "jar-stash"

        if ("$profile".isEmpty()) {
            sh """JENKINS_NODE_COOKIE=dontKillMe
                    setsid java -jar $jarName &"""
        } else {
            sh """JENKINS_NODE_COOKIE=dontKillMe
                    setsid java -jar $jarName --spring.profiles.active=$profile &"""
        }
    }
}

def call(String buildServer, String[] deployServers, String remoteUrl, String credentialsId, boolean rebuild, Map<String, String> profile, String pomDir = './') {
    def pom
    def jarName
    node(buildServer) {
        stage('Checkout') {
            checkout(remoteUrl, credentialsId)
            dir(pomDir) {
                pom = readMavenPom file: 'pom.xml'
                jarName = "${pom.artifactId}-${pom.version}.${pom.packaging}"
            }
        }

        stage('Build') {
            if (isChanged(currentBuild) || rebuild) {
                build("$pomDir/target", "$JAR_RUNNING_PATH", jarName)
            } else {
                echo "未检测到代码变化,不需要重新构建,已忽略Build步骤."
            }

            dir("$JAR_RUNNING_PATH") {
                stash name: "jar-stash", includes: "$jarName"
            }
        }
    }

    stage('Deploy') {
        if (deployServers.length > 0) {
            for (server in deployServers) {
                node(server) {
                    echo "deploying $server"
                    if (null == profile || profile.isEmpty() || null == profile[server]) {
                        deploy(rebuild, pom, "$WORKSPACE", "$JAR_RUNNING_PATH", "${env.SPRING_PROFILES_ACTIVE ?: ''}")
                    } else {
                        deploy(rebuild, pom, "$WORKSPACE", "$JAR_RUNNING_PATH", profile[server])
                    }
                }
            }
        } else {
            echo "没有指定服务器,不需要重新发布,已忽略Deploy步骤."
        }
    }
}

