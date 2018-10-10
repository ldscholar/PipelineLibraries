//var/artifactPipeline.groovy

def checkout(String remoteUrl, String credentialsId) {
    def scm = [$class              : 'SubversionSCM',
               filterChangelog     : false,
               ignoreDirPropChanges: false,
               includedRegions     : '',
               locations           : [[credentialsId: "$credentialsId", depthOption: 'infinity', ignoreExternalsOption: true, local: '.', remote: "$remoteUrl"]],
               quietOperation      : true,
               workspaceUpdater    : [$class: 'UpdateUpdater']]
    return checkout(scm)
}

def isChanged(build) {
    def changeLogSets = build.changeSets
    if (null == changeLogSets || changeLogSets.isEmpty()) {
        return false
    } else {
        return true
    }
}

def build(String targetDir, String jarSavePath, String jarName, String scmRevision) {
    String buildTime = new Date().format('yyyy-MM-dd HH:mm:ss')
    sh "mvn clean package --quiet -DscmRevision=\"${scmRevision}\" -DbuildTime=\"${buildTime}\""
    //打完包之后,复制jar包到$jarRunningPath("/home/ways")下面
    dir("$jarSavePath") {
        if (fileExists("$jarName")) {
            sh "mv -b ./$jarName ./backup/$jarName"
        }

        sh "mv -f $WORKSPACE/$targetDir/$jarName ./$jarName"
    }
}

def deploy(boolean rebuild, String jarName, String jarNameIgnoreVersion, String workspace, String jarRunningPath, String profile, String xms, String xmx) {
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
                    setsid java -server -Xms$xms -Xmx$xmx -jar $jarName 1>/dev/null 2>/dev/null &"""
        } else {
            sh """JENKINS_NODE_COOKIE=dontKillMe
                    setsid java -server -Xms$xms -Xmx$xmx -jar $jarName --spring.profiles.active=$profile 1>/dev/null 2>/dev/null &"""
        }
    }
}

def reboot(String jarName, String jarNameIgnoreVersion, String jarRunningPath, String profile, String xms, String xmx) {
    try {
        sh "ps -ef | grep $jarNameIgnoreVersion | grep -v grep | awk '{print \$2}' | xargs kill -9"
    } catch (err) {
        // nothing
    }

    dir("$jarRunningPath") {
        if (fileExists("$jarName")) {
            if ("$profile".isEmpty()) {
                sh """JENKINS_NODE_COOKIE=dontKillMe
                    setsid java -server -Xms$xms -Xmx$xmx -jar $jarName 1>/dev/null 2>/dev/null &"""
            } else {
                sh """JENKINS_NODE_COOKIE=dontKillMe
                    setsid java -server -Xms$xms -Xmx$xmx -jar $jarName --spring.profiles.active=$profile 1>/dev/null 2>/dev/null &"""
            }
        } else {
            echo "启动失败! 未找到上次构建的jar包,您需要重新构建项目."
            currentStage.result = 'FAILURE'
        }
    }
}

/**
 * 入口函数
 * @param buildServer 执行构建的服务器
 * @param deployServers 需要发布的服务器
 * @param remoteUrl 代码checkout路径
 * @param credentialsId scm认证id
 * @param rebuild 强制重新构建
 * @param profile spring.profiles.active参数
 * @param pomDir pom相对路径
 * @param xms JVM最大内存大小
 * @param xmx JVM初始内存大小
 * @return
 */
def call(String buildServer, String[] deployServers, String remoteUrl, String credentialsId, boolean rebuild, Map<String, String> profile, String pomDir = './', String xms = '1024m', String xmx = '1024m') {
    final String EXTEND = "extend"
    def pom
    def jarName
    def jarNameIgnoreVersion
    String scmRevision
    boolean rebootOnly = false

    node(buildServer) {
        stage('Checkout') {
            //即使指定了rebootOnly也需要checkout,不然根据SVN自动发布会失效
            scmRevision = checkout(remoteUrl, credentialsId).SVN_REVISION

            dir(pomDir) {
                pom = readMavenPom file: 'pom.xml'
                jarName = "${pom.artifactId}-${pom.version}.${pom.packaging}"
                jarNameIgnoreVersion = "${pom.artifactId}-.*.${pom.packaging}"
            }
        }

        stage('Build') {
            if (isChanged(currentBuild) || rebuild) {
                build("$pomDir/target", "$JAR_RUNNING_PATH", jarName, scmRevision)
                dir("$JAR_RUNNING_PATH") {
                    stash name: "jar-stash", includes: "$jarName"
                }
            } else {
                rebootOnly = true
                echo "不需要重新构建,将使用上次构建的jar包启动服务."
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
                        reboot(jarName, jarNameIgnoreVersion, "$JAR_RUNNING_PATH", activeProfile, xms, xmx)
                    } else {
                        echo "deploying $server"
                        deploy(rebuild, jarName, jarNameIgnoreVersion, "$WORKSPACE", "$JAR_RUNNING_PATH", activeProfile, xms, xmx)
                    }
                }
            }
        } else {
            echo "没有指定服务器,不需要重新发布,已忽略Deploy步骤."
        }
    }
}

