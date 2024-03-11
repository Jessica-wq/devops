package org.porvenir
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import java.text.SimpleDateFormat;


/**********************************************************************************************************
*Metodo que permite enmascarar un string
*Paramatros:
    - <List>: Lista de listas que contienen la variable y el valor que queremos ocultar
    Sintaxis para llamar al metodo:
    withSecretEnv(
      [
        [var: 'var1', password: "${value1}"],
        [var: 'var2', password: "${value2}"]
      ]
    )

***/
def withSecretEnv(List<Map> varAndPasswordList, Closure closure) {
  wrap([$class: 'MaskPasswordsBuildWrapper', varPasswordPairs: varAndPasswordList]) {
    withEnv(varAndPasswordList.collect { "${it.var}=${it.password}" }) {
      closure()
    }
  }
}


/**********************************************************************************************************
*Metodo que permite validar si estamos trabajando con alguna de las ramas principales (main. release o develop)
*Paramatros:
    - stageName: string con el nombre el stage 
    - branch: string con la rama
    - body: cuerpo, todo lo que va dentro de las {} cuando llamamos el metodo
***/
def mainStage(Map pipelineParams = [:], Closure body) {
    def MAIN_BRANCHES = [
        'main',
        'release',
        'develop'
    ]
    stage(pipelineParams.stageName) {
        when(pipelineParams.branch in MAIN_BRANCHES) {
            body()
        }
    }
}
def when(boolean condition, body) {
    def config = [:]
    body.resolveStrategy = Closure.OWNER_FIRST
    body.delegate = config

    if (condition) {
        body()
    } else {
        Utils.markStageSkippedForConditional(STAGE_NAME)
    }
}


/**********************************************************************************************************
*Metodo para traer información de los archivos json de la carpeta resources
*Paramatros (deben coincidir con las llaves en el archivo json):
    - jsonName: String, nombre del archivo json a leer
    - project: String, nombre del proyecto del que queremos recuperar la info
    - environment: String, ambiente de donde queremos recuperar la info (main, develop, release, drp)
    - object: String, Objeto o caracteristica que queremos recuperar
*El metodo usa el Utility readJSON de jenkins para leer un json y devuelve un string con el valor
***/
def infoJson(Map pipelineParams = [:]){
    def request = libraryResource "${pipelineParams.jsonName}"
    def info = readJSON text: request
    def project = pipelineParams.project
    def environment = pipelineParams.environment
    def object = pipelineParams.object
  return info."${project}"."${environment}"."${object}"
}

/**********************************************************************************************************
*Metodo para traer información de los archivos json de la carpeta resources
*Paramatros (deben coincidir con las llaves en el archivo json):
    - jsonName: String, nombre del archivo json a leer
    - project: String, nombre del proyecto del que queremos recuperar la info
    - property: String, propiedad que queremos recuperar
*El metodo usa el Utility readJSON de jenkins para leer un json y devuelve un string con el valor
***/
def infoJsonProperty(Map pipelineParams = [:]){
    def request = libraryResource "${pipelineParams.jsonName}"
    def info = readJSON text: request
    def project = pipelineParams.project
    def property = pipelineParams.property
  return info."${project}"."${property}"
}

/*
 metodo que obtiene por emdio de clases de java el user de ejcucion de pipeline jenkins.
*/

def getBuildUser() {
    def user = ''

        try {
             user = currentBuild.rawBuild.getCause(Cause.UserIdCause).getUserId()
        } catch(Exception ex) {
             println "\n\n-- se ejecuto por medio de pull request se captura el usuario del payload \n";
             user = (env.userGit == null ? 'anonimo': env.userGit)
        }
    return user
}

/**********************************************************************************************************
*Metodo para imprimir mensajes en el log con colores usando el plugin AnsiColor
*Paramatros:
    - message: String, mensaje a imprimir en el log
    - type: String, tipo de mensaje:
        Opciones de type:
            - INFO: imprime el mensaje en color azul
            - ERROR: imprime el mensaje en color rojo
            - SUCCESS: imprime el mensaje en color verde
            - WARNING: imprime mensaje en color amarillo 
*/
def printm( message, type){
    def date = new Date()
    def sdf = new SimpleDateFormat("HH:mm:ss")
    def color
    switch(type){
        case "ERROR":
            color = "41"
            break;
        case "INFO":
            color = "46"
            break;
        case "SUCCESS":
            color = "42"
            break;
        case "WARNING":
            color = "43"
            break;
    }
    ansiColor('xterm'){
        println("\033[${color}m********** ${message} ********** ${sdf.format(date)}\033[0m")
    }
}

def infoEpicYaml(Map pipelineParams = [:]){
    def request = libraryResource "${pipelineParams.yamlName}"
    def info = readYaml text: request
   
  return info
}
