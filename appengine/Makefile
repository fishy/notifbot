GO=go
GCLOUD=gcloud
p=notification-bot

.PHONY: deploy tail

deploy:
	$(GO) mod tidy
	$(GO) test
	$(GCLOUD) app deploy --project=$(p)

tail:
	$(GCLOUD) app logs tail -s default --project=$(p)
