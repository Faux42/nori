/*
 * This file is part of nori.
 * Copyright (c) 2014 Tomasz Jan Góralczyk <tomg@fastmail.uk>
 * License: ISC
 */

package com.cuddlesoft.norilib.test;

import com.cuddlesoft.norilib.clients.Danbooru;
import com.cuddlesoft.norilib.clients.SearchClient;

/**
 * Tests for the Danbooru 2.x API.
 */
public class DanbooruTests extends SearchClientTestCase {
  // TODO: Test API key authentication.

  @Override
  protected SearchClient createSearchClient() {
    return new Danbooru("Danbooru", "https://danbooru.donmai.us");
  }
}
