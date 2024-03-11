/**********************************************************************************************************
*Metodo para construir con mvn
***/
def buildMvn() {
    stage('build') {
        sh "mvn clean install package"
    }
}

/**********************************************************************************************************
*Metodo para construir con npm
***/
def buildNpm() {
    stage('build') {
        //sh "npm install --save --legacy-peer-deps"
        sh "npm install"
        sh "npm run build"
    }
}

def runBuildNpm(String cmd ){
    sh "npm ${cmd}"
}

def runGradleCmd(String cmd) {
    sh "chmod +x ./gradlew && ./gradlew ${cmd}"
}

def buildGradle(Map pipelineParams = [:], Closure body = {}) {
    stage('build') {
        runGradleCmd(pipelineParams.buildCmd)
        body()
    }
}

/**********************************************************************************************************
*Metodo para construir requiremets de python
***/
def buildPython(){
    stage('build'){
        sh "pip install flask"
        sh "pip freeze > requirements.txt"
        sh "cat requirements.txt"
    }
    
}

def buildAnt(Map pipelineParams = [:]) {
  
  sh """
    ant -buildfile "${pipelineParams.routeBuild}" ${pipelineParams.propertiesModulos} -Dworkspace=${WORKSPACE}
  """
}
