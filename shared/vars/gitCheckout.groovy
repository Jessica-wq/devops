/***********************************************************************************************************
*Script que realiza el clone de las fuentes 
*Variables:
    - branch: string, rama donde se realizará el clone
    - repo: String, url del repositorio a clonar
    - org: (Temporal), String, permite clonar fuentes de la organización AFP (PorvenirAFP)
***/
def call(Map pipelineParams = [:]) {
    stage('checkout') {
        checkout([
            $class: 'GitSCM',
            branches: [[name: pipelineParams.branch]],
            extensions: [[
                $class: 'CloneOption',
                noTags: false,
                reference: '',
                shallow: false
            ]],
            submoduleCfg: [],
            userRemoteConfigs:  [[
                credentialsId:  pipelineParams.org == 'PorvenirAFP'? 'git-credentials-goya': 'github-emu',
                refspec: '+refs/heads/*:refs/remotes/origin/* +refs/pull/*/head:refs/remotes/origin/PR-*',
                url: pipelineParams.repo
            ]]
        ])
    }
}
