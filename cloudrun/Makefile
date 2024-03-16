GO=go
GCLOUD=gcloud

project=notification-bot
region=us-central1
cloudrunname=notifbot
image=notifbot

secret_telegram_token=telegram-token

gcloud=$(GCLOUD) --project=$(project)
docker_image=$(region)-docker.pkg.dev/$(project)/$(image)/$(image)
full_git_version=$(shell git rev-parse HEAD)
version_tag=$(shell echo $(full_git_version) | cut -c1-12)

.PHONY: deploy

# if you deploy your own bot, override BOT_NAME env with your bot's name.
# default is AndroidNotificationBot.
deploy:
	@echo Building $(docker_image)...
	$(gcloud) builds submit --tag $(docker_image)
	@echo Pushing to Google Cloud Run...
	$(gcloud) run deploy --image $(docker_image) \
		--platform managed \
		--allow-unauthenticated \
		--execution-environment gen2 \
		--cpu 1 \
		--memory 512Mi \
		--region $(region) $(cloudrunname) \
		--update-env-vars CLOUD_PROJECT_ID=$(project) \
		--update-env-vars VERSION_TAG=$(version_tag) \
		--set-secrets "SECRET_TELEGRAM_TOKEN=$(secret_telegram_token):latest"

.PHONY: update-secrets

update-secrets:
	$(gcloud) run services update $(cloudrunname) \
		--set-secrets "SECRET_TELEGRAM_TOKEN=$(secret_telegram_token):latest"
