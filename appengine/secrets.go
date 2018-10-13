package main

import (
	"context"

	"cloud.google.com/go/datastore"
)

const (
	secretsKind = "secrets"

	tokenID = "telegram-token"
	redisID = "redis-url"
)

type secretEntity struct {
	Value string `datastore:"value,noindex"`
}

func getSecret(ctx context.Context, id string) (string, error) {
	key := datastore.NameKey(secretsKind, id, nil)
	e := new(secretEntity)
	if err := dsClient.Get(ctx, key, e); err != nil {
		return "", err
	}
	return e.Value, nil
}
