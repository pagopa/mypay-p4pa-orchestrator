locals {
  # Repo
  github = {
    org        = "pagopa"
    repository = "mypay-p4pa-orchestrator"
  }

  env_secrets   = {}
  env_variables = {}

  repo_secrets = var.env_short == "p" ? {
    ADMIN_GITHUB_TOKEN_RW = data.azurerm_key_vault_secret.github_token[0].value
    SONAR_TOKEN           = data.azurerm_key_vault_secret.sonar_token[0].value
    SLACK_WEBHOOK_URL     = data.azurerm_key_vault_secret.slack_webhook[0].value
    AZURE_DEVOPS_TOKEN    = data.azurerm_key_vault_secret.azure_devops_token[0].value
  } : {}

  repo_env = var.env_short == "p" ? {
    SONARCLOUD_PROJECT_NAME = "mypay-p4pa-orchestrator"
    SONARCLOUD_PROJECT_KEY  = "pagopa_mypay-p4pa-orchestrator"
    SONARCLOUD_ORG          = "pagopa"
  } : {}

  map_repo = {
    "dev" : "*",
    "uat" : "uat"
    "prod" : "main"
  }
}
