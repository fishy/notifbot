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
	if redisClient == nil {
		return ErrNoRedis
	}
	return redisClient.Set(e.getKey(), e.Token, 0).Err()
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
		errorLog.Printf("Failed to delete datastore key %v: %v", key, err)
	}
	var err error
	redisKey := e.getKey()
	if redisClient == nil {
		err = ErrNoRedis
	} else {
		err = redisClient.Del(redisKey).Err()
	}
	if err != nil {
		errorLog.Printf("Failed to delete redis key %v: %v", redisKey, err)
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
	if redisClient != nil {
		key := e.getKey()
		value, err := redisClient.Get(key).Result()
		if err == nil {
			e.Token = value
			return e
		}
		if !isNotExist(err) {
			errorLog.Printf("Failed to get redis key %s: %v", key, err)
		}
	}
	key := e.datastoreKey()
	if err := dsClient.Get(ctx, key, e); err != nil {
		errorLog.Printf("Failed to get datastore key %v: %v", key, err)
		return nil
	}
	if err := e.SaveMemcache(); err != nil {
		errorLog.Printf("Failed to save redis key %s: %v", e.getKey(), err)
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
		errorLog.Printf("Failed to save redis chat %d: %v", id, err)
	}
	if err := e.SaveDatastore(ctx); err != nil {
		errorLog.Printf("Failed to save datastore chat %d: %v", id, err)
	}
	return e
}

func randomString(ctx context.Context, len int) string {
	buf := make([]byte, len)
	n, err := rand.Read(buf)
	if err != nil || n != len {
		errorLog.Printf(
			"Failed to generate random string: read %d/%d, err = %v",
			n,
			len,
			err,
		)
	}
	return hex.EncodeToString(buf)
}
