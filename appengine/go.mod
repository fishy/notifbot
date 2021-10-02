module github.com/fishy/notifbot/appengine

go 1.16

require (
	cloud.google.com/go/datastore v1.3.0
	cloud.google.com/go/monitoring v1.0.0
	cloud.google.com/go/secretmanager v1.0.0
	github.com/blendle/zapdriver v1.3.1
	go.uber.org/zap v1.16.0
	google.golang.org/appengine/v2 v2.0.0-rc2
	google.golang.org/genproto v0.0.0-20210921142501-181ce0d877f6
	google.golang.org/protobuf v1.27.1
	gopkg.in/yaml.v2 v2.2.4 // indirect
)
