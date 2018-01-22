//src/SimplePipeline.groovy
def checkout(credentialsId, remoteUrl) {
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

def build() {
    if (env.isChanged.equals('true')) {
        sh 'mvn clean deploy'
    }
    def pom = readMavenPom file: 'pom.xml'
    env.jar = pom.artifactId + '-' + pom.version + '.jar'
    env.artifact = pom.parent.groupId + ':' + pom.artifactId + ':' + pom.version
}

def deploy(profile) {
    sh "mvn dependency:get -DremoteRepositories=$NEXUS -Dartifact=$artifact -Ddest=$WORKSPACE"
    try {
        sh "ps -ef | grep $jar | grep -v grep | awk '{print \$2}' | xargs kill -9"
    } catch (err) {
        echo "WARNING: 旧服务关闭失败,可能是旧服务未启动或已关闭"
    }

    dir("$JAR_RUNNING_PATH") {
        if (fileExists("$jar")) {
            sh "mv -b ./$jar ./backup/$jar"
        }
        sh "mv -f $WORKSPACE/$jar ./$jar"
        sh """JENKINS_NODE_COOKIE=dontKillMe
                    setsid java -jar $jar --spring.profiles.active=$profile &"""
    }
}

