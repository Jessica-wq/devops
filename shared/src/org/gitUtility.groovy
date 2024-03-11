package org.porvenir
import groovy.json.JsonSlurper

/**********************************************************************************************************
*Metodo que permite validar si el mensaje del commit tiene la sintaxis correcta
*Paramatros:
    - commitMessage: string con el mensaje del commit
***/
def getCommitType(commitMessage) {
        def pattern = /^((feat|fix|BCH|docs|other|chore|build\(deps\)):\s([A-Z]*-\d*:\s)?[A-Z]([a-zA-Z]|\d*|\W|\s)*|Delete\strigger)$/
        def matcher = commitMessage =~ pattern
        if(!matcher.find()) {
            echo "\u274C GitMessage: -- ${commitMessage} -- is not valid \u274C"
            throw new Exception("Git Message in invalid")
        }
        def commitType =  commitMessage.tokenize(':')[0]
        
        return commitType
}

/**********************************************************************************************************
*Metodo que permite identificar la cantidad de paginas que tiene github distribuyendo la pagina entre 100 repositorios por pag
*Paramatros:
    - org: string con el nombre de la organizacion de github
    - TOKEN_GIT: secret de authenticacion de github
***/
def getPagesRepositoriesGithub(ORG, TOKEN_GIT, page = 1, repos = []) {
    def TOTALPAGES = sh(script: '''curl -I -i -H "Authorization: Bearer ${TOKEN_GIT}" -H "Accept: application/vnd.github.v3+json" -s https://api.github.com/orgs/${ORG}/repos\\?per_page\\=100 |
   grep -i link: 2>/dev/null|sed \'s/link: //g\'|
   awk -F\',\' -v  ORS=\'\\n\' \'{ for (i = 1; i <= NF; i++) print $i }\'|
   grep -i last|
   awk \'{print $1}\' |
    tr -d \'\\<\\>\' |
    tr \'\\?\\&\' \' \'|awk \'{print $3}\'|
    tr -d \'=;page\'''', returnStdout:true).trim()
    
    return TOTALPAGES
}

/**********************************************************************************************************
*Metodo que permite identificar el nombre de los repositorios que tiene github
*Paramatros:
    - org: string con el nombre de la organizacion de github
    - TOKEN_GIT: secret de authenticacion de github
    - numPages: Cantidad de paginas identificadas en el methodo anterior
***/
def getReposInfoGithub(ORG, TOKEN_GIT, numPages) {

   def numIntPages = numPages.toInteger()
    for (int i = 1; i <= numIntPages; i++) {
        def scriptCurl = "curl -L -H 'Accept: application/vnd.github+json' -H 'Authorization: Bearer ${TOKEN_GIT}' -H 'X-GitHub-Api-Version: 2022-11-28' https://api.github.com/orgs/${ORG}/repos?page="+i+"\\&per_page=100 | grep full_name | awk '{print \$2}' | tr -d '\",/' | sed 's/${ORG}//g' >> salida.log"
      
        sh """${scriptCurl}"""
    }
    sh '''
    
        archivo="salida.log"
        while IFS= read -r linea
        do
            curl -L -H "Accept: application/vnd.github+json" -H "Authorization: Bearer ${TOKEN_GIT}" -H "X-GitHub-Api-Version: 2022-11-28" https://api.github.com/repos/${ORG}/$linea > temp.txt

            file="temp.txt"
            string_id=\'"archived": true\'
            if grep -q "$string_id" "$file"; then
                echo "[INFO]-Repo_archivado "$linea >> resultado.log
            else
                echo "[INFO]-Repo_Activo "$linea >> resultado.log
            fi
            
        done < "$archivo"
    '''
    sh "echo '----------------------------------------------------------------------'"
    sh "cat resultado.log"
}
def exist_repo(REPO, ORG, GIT_TOKEN){
    def result = sh(script: '''curl -s -o /dev/null -w "%{http_code}" -H "Authorization: token '''+GIT_TOKEN+'''" "https://api.github.com/repos/'''+ORG+'''/'''+REPO+'''"''', returnStdout:true).trim()
    return result
}
