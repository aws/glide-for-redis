// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/glide/api"
)

const (
	keyName      = "key"
	initialValue = "value"
	anotherValue = "value2"
)

func (suite *GlideTestSuite) TestSetAndGet_noOptions() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		suite.verifyOK(client.Set(keyName, initialValue))
		result, err := client.Get(keyName)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), initialValue, result)
	})
}

func (suite *GlideTestSuite) TestSetAndGet_byteString() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		invalidUTF8Value := "\xff\xfe\xfd"
		suite.verifyOK(client.Set(keyName, invalidUTF8Value))
		result, err := client.Get(keyName)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), invalidUTF8Value, result)
	})
}

func (suite *GlideTestSuite) TestSetWithOptions_ReturnOldValue() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		suite.verifyOK(client.Set(keyName, initialValue))

		opts := &api.SetOptions{ReturnOldValue: true}
		result, err := client.SetWithOptions(keyName, anotherValue, opts)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), initialValue, result)
	})
}

func (suite *GlideTestSuite) TestSetWithOptions_OnlyIfExists_overwrite() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := "TestSetWithOptions_OnlyIfExists_overwrite"
		suite.verifyOK(client.Set(key, initialValue))

		opts := &api.SetOptions{ConditionalSet: api.OnlyIfExists}
		suite.verifyOK(client.SetWithOptions(key, anotherValue, opts))

		result, err := client.Get(key)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), anotherValue, result)
	})
}

func (suite *GlideTestSuite) TestSetWithOptions_OnlyIfExists_missingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := "TestSetWithOptions_OnlyIfExists_missingKey"
		opts := &api.SetOptions{ConditionalSet: api.OnlyIfExists}
		result, err := client.SetWithOptions(key, anotherValue, opts)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "", result)
	})
}

func (suite *GlideTestSuite) TestSetWithOptions_OnlyIfDoesNotExist_missingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := "TestSetWithOptions_OnlyIfDoesNotExist_missingKey"
		opts := &api.SetOptions{ConditionalSet: api.OnlyIfDoesNotExist}
		suite.verifyOK(client.SetWithOptions(key, anotherValue, opts))

		result, err := client.Get(key)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), anotherValue, result)
	})
}

func (suite *GlideTestSuite) TestSetWithOptions_OnlyIfDoesNotExist_existingKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := "TestSetWithOptions_OnlyIfDoesNotExist_existingKey"
		opts := &api.SetOptions{ConditionalSet: api.OnlyIfDoesNotExist}
		suite.verifyOK(client.Set(key, initialValue))

		result, err := client.SetWithOptions(key, anotherValue, opts)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "", result)

		result, err = client.Get(key)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), initialValue, result)
	})
}

func (suite *GlideTestSuite) TestSetWithOptions_KeepExistingExpiry() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := "TestSetWithOptions_KeepExistingExpiry"
		opts := &api.SetOptions{Expiry: &api.Expiry{Type: api.Milliseconds, Count: uint64(2000)}}
		suite.verifyOK(client.SetWithOptions(key, initialValue, opts))

		result, err := client.Get(key)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), initialValue, result)

		opts = &api.SetOptions{Expiry: &api.Expiry{Type: api.KeepExisting}}
		suite.verifyOK(client.SetWithOptions(key, anotherValue, opts))

		result, err = client.Get(key)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), anotherValue, result)

		time.Sleep(2222 * time.Millisecond)
		result, err = client.Get(key)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "", result)
	})
}

func (suite *GlideTestSuite) TestSetWithOptions_UpdateExistingExpiry() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := "TestSetWithOptions_UpdateExistingExpiry"
		opts := &api.SetOptions{Expiry: &api.Expiry{Type: api.Milliseconds, Count: uint64(100500)}}
		suite.verifyOK(client.SetWithOptions(key, initialValue, opts))

		result, err := client.Get(key)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), initialValue, result)

		opts = &api.SetOptions{Expiry: &api.Expiry{Type: api.Milliseconds, Count: uint64(2000)}}
		suite.verifyOK(client.SetWithOptions(key, anotherValue, opts))

		result, err = client.Get(key)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), anotherValue, result)

		time.Sleep(2222 * time.Millisecond)
		result, err = client.Get(key)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "", result)
	})
}

func (suite *GlideTestSuite) TestSetWithOptions_ReturnOldValue_nonExistentKey() {
	suite.runWithDefaultClients(func(client api.BaseClient) {
		key := "TestSetWithOptions_ReturnOldValue_nonExistentKey"
		opts := &api.SetOptions{ReturnOldValue: true}

		result, err := client.SetWithOptions(key, anotherValue, opts)

		assert.Nil(suite.T(), err)
		assert.Equal(suite.T(), "", result)
	})
}
