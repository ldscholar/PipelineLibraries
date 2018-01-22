//src/util/PipelineStage.groovy
package utils

class PipelineStage {
    static def checkout(String remoteUrl, String credentialsId) {
        def scm = [$class              : 'SubversionSCM',
                   filterChangelog     : false,
                   ignoreDirPropChanges: false,
                   includedRegions     : '',
                   locations           : [[credentialsId: "$credentialsId", depthOption: 'infinity', ignoreExternalsOption: true, local: '.', remote: "$remoteUrl"]],
                   quietOperation      : true,
                   workspaceUpdater    : [$class: 'UpdateUpdater']]
        checkout(scm)
    }

    static def isChanged(currentBuild) {
        def changeLogSets = currentBuild.changeSets
        if (null == changeLogSets || changeLogSets.isEmpty()) {
            return false
        } else {
            return true
        }
    }

    static def build() {
        sh 'mvn clean deploy'
    }

    static def deploy(String remoteRepositories, String workspace, String jarRunningPath, String profile) {
        def pom = readMavenPom file: 'pom.xml'
        def jar = pom.artifactId + '-' + pom.version + '.jar'
        def artifact = pom.parent.groupId + ':' + pom.artifactId + ':' + pom.version

        sh "mvn dependency:get -DremoteRepositories=$remoteRepositories -Dartifact=$artifact -Ddest=$workspace"
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
}