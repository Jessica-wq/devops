
import groovy.transform.Field
import org.porvenir.gitUtility
import groovy.json.JsonSlurper
 

@Field def gitUtil = new gitUtility()


/***********************************************************************************************************
*Metodo que retorna un string (version) con la version configurada en base a los commits y la version anterior
*Variables:
    - tags: string con la salida del comando git tags (sintaxis de los tags: vX.X.X)
    - version: string contiene la version actual, se configura dependiendo de los commits
    - major: Version principal (X.0.0)
    - minor: Version menor (0.X.0)
    - patch: Parche (0.0.X)
    Los 3 numeros se configuran dependiendo del mensajes de los commits
    si el mensaje contiene la sintaxis "BCH: <Mensaje>" se aumenta en 1 la version principal (X+1).x.x
    si el mensaje contiene la sintaxis "feat: <Mensaje>" se aumenta en 1 la version menor x.(X+1).x
    si el mensaje contiene la sintaxis "fix: <Mensaje>" se aumenta en 1 el parche x.x.(X+1)
***/
def riseVersionNumber() { 
    stage('Verify Commit') { 
        def tags = sh(returnStdout: true, script: "git tag")
        def gitMessages = []
        def version = '1.0.0'
        echo tags
        boolean bandera = true 
        if(!tags){
            def commitList = sh(returnStdout: true, script: "git log --no-merges --pretty=format:'%s%n'")
            echo " La commit list es: ${commitList}"
            gitMessages = commitList.trim().tokenize('\n').reverse()
            echo " El gitMessages es: ${gitMessages}"
            gitMessages.remove(0)
            echo " El gitMessages 1.2 es: ${gitMessages}"
            echo "uno"
        }
        else {
            echo "dos"
            version = sh(returnStdout: true, script: "git tag -l --sort=v:refname 'v*' | tail -1")
            version = version.trim()
            if (version?.trim()){
                echo "La version es nula por lo tanto verifique que el tag venga asi |vx.x.x| todo en minuscula"
            }
            gitMessages = sh(returnStdout: true, script: "git log ${version}... --no-merges  --pretty=format:'%s'")
            echo " El gitMessages 2 es: ${gitMessages}"
            gitMessages = gitMessages.trim().tokenize('\n').reverse()
            echo " El gitMessages 3 es: ${gitMessages}"
            version = version.replaceAll('v','')
            echo "la version es" + version
        }
        gitMessages.each{ gitMessage ->
            def tokenizedVersion = version.trim().replaceAll('v','').tokenize('.')
            def major = tokenizedVersion[0].toInteger()
            def minor = tokenizedVersion[1].toInteger()
            def patch = tokenizedVersion[2].toInteger()
            def commitType = gitUtil.getCommitType(gitMessage)

            //BCH = Bracking change
            if(commitType == 'BCH') { 
                major += 1 
                minor = 0 
                patch = 0 
            } 
            if(commitType == 'feat') { 
                minor += 1
                patch = 0 
            } 
            if(commitType == 'fix') { 
                patch += 1 
            } 
            version = "${major}.${minor}.${patch}"
        }    
        echo "Using Version: ${version}"
        return version
    }
}


/**********************************************************************************************************
*Metodo que crear el tag en git con la sintaxis vX.X.X y realiza el push a github
*Paramatros:
    - tagVersion: string que contiene la version sintaxis X.X.X
    - repo: string con la url del repositorio
    - branch: string con la rama
***/
def commitTag(Map pipelineParams) {
    if (pipelineParams.branch == 'main'){
        stage('commitTag'){
            def currentVersion = sh(returnStdout: true, script: "git tag -l --sort=v:refname 'v*' | tail -1")
            def newVersion = pipelineParams.tagVersion
            echo pipelineParams.tagVersion
            echo currentVersion
            echo newVersion
                withCredentials([usernamePassword(credentialsId: 'github-emu', passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
                    sh("git tag v${pipelineParams.tagVersion}")
                    sh("git push https://${GIT_USERNAME}:${GIT_PASSWORD}@${pipelineParams.repo.split('//')[1]} --tags")
                }
        }
    }
}


/**********************************************************************************************************
*Metodo que permite leer el json del payload y retorna un string con el valor correspondiente
*Paramatros:
    - request: string con la consulta que queremos realizar
*Opciones:
    - title: 
    - urlRepo: devuelve la url del repositorio en protocolo https
    - action: devuelve accion del pull request (opened , closed)
    - user: devuelve el usuario que abrió el pull request
    - environment: devuelve la rama hacia donde se hizo el pull request 
    - branch: devuelve la rama desde donde se hizo el pull request
    - nameProject: devuelve el nombre del proyecto
***/
def infoPayload(request){
    def result = readJSON text: payload
    if(request == 'title'){
        return result.pull_request.title
    }else if(request == 'urlRepo'){
        return result.repository.clone_url
    }else if(request == 'action'){
        return result.action
    }else if(request == 'user'){
        return result.pull_request.user.login
    }else if(request == 'branch'){
        return result.pull_request.head.ref
    }else if(request == 'environment'){
        return result.pull_request.base.ref
    }else if(request == 'nameProject'){
        return result.repository.name
    }else if(request == 'version'){
        def pattern = /.*version=/
        return result.pull_request.title.replaceAll(pattern,"").trim()
    }else if(request == 'issue'){
        def pattern = /.*issue=/
        return result.pull_request.title.replaceAll(pattern,"").trim()
    }
}


/**********************************************************************************************************
*Metodo que retorna un booleano dependiendo del mensaje que contenga el pull request
*Paramatros:
    - process: string con el proceso a consultar
*Opciones:
    - CI: devuelve booleano dependiendo si el mensaje del pull request contiene "ci=true" 
    - CD: devuelve booleano dependiendo si el mensaje del pull request contiene "cd=true"
***/
def validatePayload(process){
    def action = infoPayload('action')
    def title = infoPayload('title')

    if(process == "CI" && action == "opened"){
        return title.toLowerCase().contains("ci=true") ? true : false
    }else if(process == "CD" && action == "opened"){
        return title.toLowerCase().contains("cd=true") ? true : false
    }else{
        return false
    }
}

def imageVersionGit(){

    sh "git config --global --add safe.directory ${workspace}"
    def lastCommit = sh(returnStdout: true, script: "git log --oneline |awk 'NR == 1 {print \$1}'")
    println "Last Commit: "+lastCommit

    return lastCommit.trim()
}
def getRepositories(Map pipelineParams = [:]) { 
    def ID_TOKEN
    def fileName = "repo-list.txt"
    def commitMessage = "Actualizar lista de repositorios"
  
    if(pipelineParams.ORG == "PorvenirAFP"){
        ID_TOKEN="github-token"
    }else if(pipelineParams.ORG == "PorvenirEMU"){
        ID_TOKEN="GITHUB_TOKEN_EMU"
    }
  
    withCredentials([string(credentialsId: ID_TOKEN, variable: 'TOKEN_GIT')]) {
      def repos = gitUtil.getPagesRepositoriesGithub(pipelineParams.ORG, TOKEN_GIT, page = 1, repos = [])
      //def content = repos.join("\n")
      println "[INFO] Total paginas: "+repos
      gitUtil.getReposInfoGithub(pipelineParams.ORG, TOKEN_GIT,repos)
    }

    
}
def MOVE_REPO_AFP_TO_EMU_NOTHISTORY(String subMetodo, repositorio){
    switch(subMetodo) {

        case "validateRepos":
            // VALIDA SI EXISTE EL REPOSITORIO EN ORGANIZACION AFP Y ORGANIZACION EMU.
            // APF
            withCredentials([string(credentialsId: "github-token", variable: 'TOKEN_GIT_AFP')]) {
                def AFP_EXIST_REPO = gitUtil.exist_repo(repositorio, "PorvenirAFP", TOKEN_GIT_AFP)
                if(AFP_EXIST_REPO == '200'){
                    println "[INFO] El repositorio ${repositorio} existe en la organizacion PorvenirAFP"
                }else{
                    error("[ERROR] El repositorio ${repositorio} NO existe en la organizacion PorvenirAFP")
                     
                }

            }
            
            // EMU
            withCredentials([string(credentialsId: "GITHUB_TOKEN_EMU", variable: 'GITHUB_TOKEN_EMUU')]) {
                def EMU_EXIST_REPO = gitUtil.exist_repo(repositorio, "PorvenirEMU", GITHUB_TOKEN_EMUU)
                if(EMU_EXIST_REPO == '200'){
                    println "[INFO] El repositorio ${repositorio} existe en la organizacion PorvenirAFP"
                }else{
                    error("[ERROR] El repositorio ${repositorio} NO existe en la organizacion PorvenirAFP")
                     
                }
            }
        break
        case "clonRepo":
            //ORIGEN AFP
            sh "mkdir ORIGIN"
            withCredentials([string(credentialsId: "github-token", variable: 'TOKEN_GIT_AFP')]) {
                sh "cd ORIGIN && git clone https://oauth2:${TOKEN_GIT_AFP}@github.com/PorvenirAFP/${repositorio}.git"
                
                dir('/home/jenkins/agent/workspace/devops/GITHUB/MOVE_REPO_AFP_TO_EMU_NOTHISTORY/ORIGIN/'+repositorio){
                    sh "git branch -r --format='%(refname:lstrip=3)' | grep  -v -e 'HEAD' >> ../ramas.log"
                    sh "cat ../ramas.log"
                    sh '''while IFS= read -r line; do
                    echo "Ejecutando linea para: $line"
                    done < "../ramas.log"'''

                }
                
            }

            //destino EMU
            sh "mkdir DESTINO"
            withCredentials([string(credentialsId: "GITHUB_TOKEN_EMU", variable: 'GITHUB_TOKEN_EMUU')]) {
                sh "cd DESTINO && git clone https://oauth2:${GITHUB_TOKEN_EMUU}@github.com/PorvenirEMU/${repositorio}.git"
            }
        break
        default:
            echo "WARNING Metodo no especificado"
    }
}
