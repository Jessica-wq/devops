/**********************************************************************************************************
*Metodo que cambia los valores de IP del proyecto por los correspondientes al Artifactory OnPremise
*Paramatros:
    - No Aplica
***/
def actualizarIPArtifactoryOnPremise(){
    println "**********************[INFO] Actualizando archivos con la IP de JFrog.**********************"
    sh 'sed -i -e s/10.33.160.12/10.110.30.82/g settings.gradle'
    sh 'sed -i -e s/10.33.160.12/10.110.30.82/g gradle/wrapper/gradle-wrapper.properties'

    script{
        def subfoldersWars = sh(returnStdout: true, script: 'ls -d modules/*').trim().split("\n")
        subfoldersWars.each { item ->
            dir(item){
                sh 'sed -i -e "s/10.33.160.12/10.110.30.82/g" build.gradle'
            }
        }
    }
}

/**********************************************************************************************************
*Metodo que cambia los valores de IP del proyecto por los correspondientes al Artifactory K8s
*Paramatros:
    - No Aplica
***/
def actualizarIPArtifactoryCloud(){
    println "**********************[INFO] Actualizando archivos con la IP de JFrog.**********************"
    sh 'sed -i -e "s/http:\\/\\/10.33.160.12:9090/https:\\/\\/devops.porvenir.com/g" settings.gradle'
    sh 'sed -i -e "s/12345678/12345678dD/g" settings.gradle'
    script{
        def subfoldersWars = sh(returnStdout: true, script: 'ls -d modules/*').trim().split("\n")
        subfoldersWars.each { item ->
            dir(item){
                sh 'sed -i -e "s/http:\\/\\/10.33.160.12:9090/https:\\/\\/devops.porvenir.com/g" build.gradle'
                sh 'sed -i -e "s/12345678/12345678dD/g" build.gradle'
                
            }
        }
    }
}

/**********************************************************************************************************
*Metodo que permite versionar un Artefacto en Artifactory
*Paramatros:
    - pattern: String, nombre del artefacto a versionar
    - target: String, path de la ruta destino donde se va a versionar el artefacto
***/
def versioninginArtifact(Map pipelineParams){
    println "**********************[INFO] Versionando componente en Artifactory.**********************"
    script{
        rtUpload (
            serverId: 'Artifactory',
            spec: """{
              "files": [
                    {
                      "pattern": "${pipelineParams.pattern}",
                      "target": "${pipelineParams.target}"
                    }
                ]
            }"""
        )
    }
}

/**********************************************************************************************************
*Metodo que permite descargar un Artefacto previamente versionado en Artifactory
*Paramatros:
    - pattern: String, nombre del artefacto a descargar
    - target: String, path de la ruta donde se encuentra el artefacto
***/
def downloadArtifact(Map pipelineParams){
    println "**********************[INFO] Descargando componente en Artifactory.**********************"
    script{
        rtDownload (
            serverId: 'Artifactory',
            spec: """{
                "files": [
                    {
                      "pattern": "${pipelineParams.pattern}",
                      "target": "${pipelineParams.target}",
                      "sortBy": ["created"],
                      "sortOrder": "desc",
                      "limit": 1
                    }
                ]
            }"""
        )
    }
}

/**********************************************************************************************************
*Metodo que permite descargar un Artefacto previamente versionado en Artifactory
*Paramatros:
    - pattern: String, nombre del artefacto a descargar
    - target: String, path de la ruta donde se encuentra el artefacto
***/
def downloadSpecificArtifact(Map pipelineParams){
    println "**********************[INFO] Descargando componente en Artifactory.**********************"
    script{
        rtDownload (
            serverId: 'Artifactory',
            spec: """{
                "files": [
                    {
                      "pattern": "${pipelineParams.pattern}",
                      "target": "${pipelineParams.target}",
                      "sortOrder": "desc",
                      "limit": 1
                    }
                ]
            }"""
        )
    }
}

/**********************************************************************************************************
*Metodo que permite versionar un Artefacto en Artifactory OnPremise
*Paramatros:
    - pattern: String, nombre del artefacto a versionar
    - target: String, path de la ruta destino donde se va a versionar el artefacto
***/
def versioninginArtifactOnPremise(Map pipelineParams){
    println "**********************[INFO] Versionando componente en Artifactory.**********************"
    script{
        rtUpload (
            serverId: 'ArtifactoryOnPremise',
            spec: """{
              "files": [
                    {
                      "pattern": "${pipelineParams.pattern}",
                      "target": "${pipelineParams.target}"
                    }
                ]
            }"""
        )
    }
}

/**********************************************************************************************************
*Metodo que permite descargar un Artefacto previamente versionado en Artifactory OnPremise
*Paramatros:
    - pattern: String, nombre del artefacto a descargar
    - target: String, path de la ruta donde se encuentra el artefacto
***/
def downloadArtifactOnPremise(Map pipelineParams){
    println "**********************[INFO] Descargando componente en Artifactory.**********************"
    script{
        rtDownload (
            serverId: 'ArtifactoryOnPremise',
            spec: """{
                "files": [
                    {
                      "pattern": "${pipelineParams.pattern}",
                      "target": "${pipelineParams.target}",
                      "sortBy": ["created"],
                      "sortOrder": "desc",
                      "limit": 1
                    }
                ]
            }"""
        )
    }
}
