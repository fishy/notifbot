package main

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"

	"cloud.google.com/go/datastore"
)

const (
	chatKey     = "chat-%d"
	chatKind    = "notifbotchats"
	tokenLength = 16
)

// EntityChat is the entity of a chat/token stored in datastore.
type EntityChat struct {
	Chat  int64  `datastore:"chat"`
	Token string `datastore:"token"`
}

func (e *EntityChat) getKey() string {
	return fmt.Sprintf(chatKey, e.Chat)
}

func (e *EntityChat) datastoreKey() *datastore.Key {
	return datastore.NameKey(chatKind, e.getKey(), nil)
}

// SaveDatastore saves this token into datastore.
func (e *EntityChat) SaveDatastore(ctx context.Context) error {
	key := e.datastoreKey()
	_, err := dsClient.Put(ctx, key, e)
	return err
}

// Delete deletes this chat token from datastore.
func (e *EntityChat) Delete(ctx context.Context) {
	key := e.datastoreKey()
	if err := dsClient.Delete(ctx, key); err != nil {
		l(ctx).Error(
			"Failed to delete datastore key",
			"err", err,
			"key", key,
		)
	}
}

// GetURL returns the url for this chat.
func (e *EntityChat) GetURL() string {
	return fmt.Sprintf(
		"%s%s%d/%s",
		globalURLPrefix,
		clientPrefix,
		e.Chat,
		e.Token,
	)
}

// GetChat gets a chat token from db.
func GetChat(ctx context.Context, id int64) *EntityChat {
	e := &EntityChat{
		Chat: id,
	}
	key := e.datastoreKey()
	if err := dsClient.Get(ctx, key, e); err != nil {
		logger := l(ctx).Error
		if errors.Is(err, datastore.ErrNoSuchEntity) {
			logger = l(ctx).Info
		}
		logger(
			"Failed to get datastore key",
			"err", err,
			"key", key,
		)
		return nil
	}
	return e
}

// NewChat creates a new chat token and saves it into db.
func NewChat(ctx context.Context, id int64) *EntityChat {
	if e := GetChat(ctx, id); e != nil {
		return e
	}
	e := &EntityChat{
		Chat:  id,
		Token: randomString(ctx, tokenLength),
	}
	if err := e.SaveDatastore(ctx); err != nil {
		l(ctx).Error(
			"Failed to save chat to datastore",
			"err", err,
			"id", id,
		)
	}
	return e
}

func randomString(ctx context.Context, size int) string {
	buf := make([]byte, size)
	n, err := rand.Read(buf)
	if err != nil || n != size {
		l(ctx).Error(
			"Failed to generate random string",
			"err", err,
			"read", n,
			"want", size,
		)
	}
	return hex.EncodeToString(buf)
}
