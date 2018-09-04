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

def deploy(boolean rebuild, String jarName, String jarNameIgnoreVersion, String workspace, String jarRunningPath, String profile) {
    try {
        sh "ps -ef | grep $jarNameIgnoreVersion | grep -v grep | awk '{print \$2}' | xargs kill -9"
    } catch (err) {
        echo "WARNING: 旧服务关闭失败,可能是旧服务未启动或已关闭"
    }

    dir("$jarRunningPath") {
        if (fileExists("$jarName")) {
            //这里不再备份jar包了
            //sh "mv -b ./$jarName ./backup/$jarName"
            sh "rm -f $jarName"
        }

        unstash "jar-stash"

        if ("$profile".isEmpty()) {
            sh """JENKINS_NODE_COOKIE=dontKillMe
                    setsid java -jar $jarName >/dev/null &"""
        } else {
            sh """JENKINS_NODE_COOKIE=dontKillMe
                    setsid java -jar $jarName --spring.profiles.active=$profile >/dev/null &"""
        }
    }
}

def reboot(String jarName, String jarNameIgnoreVersion, String jarRunningPath, String profile) {
    try {
        sh "ps -ef | grep $jarNameIgnoreVersion | grep -v grep | awk '{print \$2}' | xargs kill -9"
    } catch (err) {
        // nothing
    }

    dir("$jarRunningPath") {
        if (fileExists("$jarName")) {
            if ("$profile".isEmpty()) {
                sh """JENKINS_NODE_COOKIE=dontKillMe
                    setsid java -jar $jarName >/dev/null &"""
            } else {
                sh """JENKINS_NODE_COOKIE=dontKillMe
                    setsid java -jar $jarName --spring.profiles.active=$profile >/dev/null &"""
            }
        } else {
            echo "启动失败,未找到上次build的jar包."
        }
    }
}

def call(String buildServer, String[] deployServers, String remoteUrl, String credentialsId, boolean rebuild, Map<String, String> profile, String pomDir = './', boolean rebootOnly = false) {
    final String EXTEND = "extend"
    def pom
    def jarName
    def jarNameIgnoreVersion

    node(buildServer) {
        stage('Checkout') {
            //即使指定了rebootOnly也需要checkout,不然根据SVN自动发布会失效
            checkout(remoteUrl, credentialsId)

            dir(pomDir) {
                pom = readMavenPom file: 'pom.xml'
                jarName = "${pom.artifactId}-${pom.version}.${pom.packaging}"
                jarNameIgnoreVersion = "${pom.artifactId}-.*.${pom.packaging}"
            }
        }

        stage('Build') {
            if (rebootOnly) {
                echo "指定了rebootOnly, 已跳过Build步骤."
            } else {
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
    }

    stage('Deploy') {
        if (deployServers.length > 0) {
            def profileExtend
            if (null != profile) {
                profileExtend = "${profile[EXTEND] ? ',' + profile[EXTEND] : ''}"
            } else {
                profileExtend = ''
            }

            for (server in deployServers) {
                def activeProfile
                node(server) {
                    if (null == profile || profile.isEmpty() || null == profile[server]) {
                        activeProfile = "${env.SPRING_PROFILES_ACTIVE ?: ''}" + profileExtend
                    } else {
                        activeProfile = profile[server] + profileExtend
                    }

                    if (rebootOnly) {
                        echo "booting $server"
                        reboot(jarName, jarNameIgnoreVersion, "$JAR_RUNNING_PATH", activeProfile)
                    } else {
                        echo "deploying $server"
                        deploy(rebuild, jarName, jarNameIgnoreVersion, "$WORKSPACE", "$JAR_RUNNING_PATH", activeProfile)
                    }
                }
            }
        } else {
            echo "没有指定服务器,不需要重新发布,已忽略Deploy步骤."
        }
    }
}

