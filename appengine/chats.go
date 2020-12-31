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

// ErrNoRedis is the error returned when no redis is available.
var ErrNoRedis = errors.New("no redis available")

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

// SaveMemcache saves this token into redis.
func (e *EntityChat) SaveMemcache() error {
	if client, ok := getRedis(); ok {
		return client.Set(e.getKey(), e.Token, 0).Err()
	}
	return ErrNoRedis
}

// SaveDatastore saves this token into datastore.
func (e *EntityChat) SaveDatastore(ctx context.Context) error {
	key := e.datastoreKey()
	_, err := dsClient.Put(ctx, key, e)
	return err
}

// Delete deletes this chat token from both datastore and redis.
func (e *EntityChat) Delete(ctx context.Context) {
	key := e.datastoreKey()
	if err := dsClient.Delete(ctx, key); err != nil {
		l(ctx).Errorw(
			"Failed to delete datastore key",
			"key", key,
			"err", err,
		)
	}
	var err error
	redisKey := e.getKey()
	if client, ok := getRedis(); ok {
		err = client.Del(redisKey).Err()
	} else {
		err = ErrNoRedis
	}
	if err != nil {
		l(ctx).Errorw(
			"Failed to delete redis key",
			"key", redisKey,
			"err", err,
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
	if redisClient, ok := getRedis(); ok {
		key := e.getKey()
		value, err := redisClient.Get(key).Result()
		if err == nil {
			e.Token = value
			return e
		}
		if !isNotExist(err) {
			l(ctx).Errorw(
				"Failed to get redis key",
				"key", key,
				"err", err,
			)
		}
	}
	key := e.datastoreKey()
	if err := dsClient.Get(ctx, key, e); err != nil {
		l(ctx).Errorw(
			"Failed to get datastore key",
			"key", key,
			"err", err,
		)
		return nil
	}
	if err := e.SaveMemcache(); err != nil {
		l(ctx).Errorw(
			"Failed to save redis key",
			"key", e.getKey(),
			"err", err,
		)
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
	if err := e.SaveMemcache(); err != nil {
		l(ctx).Errorw(
			"Failed to save chat to redis",
			"id", id,
			"err", err,
		)
	}
	if err := e.SaveDatastore(ctx); err != nil {
		l(ctx).Errorw(
			"Failed to save chat to datastore",
			"id", id,
			"err", err,
		)
	}
	return e
}

func randomString(ctx context.Context, size int) string {
	buf := make([]byte, size)
	n, err := rand.Read(buf)
	if err != nil || n != size {
		l(ctx).Errorw(
			"Failed to generate random string",
			"read", n,
			"want", size,
			"err", err,
		)
	}
	return hex.EncodeToString(buf)
}
