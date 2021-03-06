/*
 * Android Notifier Desktop is a multiplatform remote notification client for Android devices.
 *
 * Copyright (C) 2010  Leandro Aparecido
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.notifier.desktop.notification.parsing.impl;

import java.util.*;

import org.junit.*;

import com.notifier.desktop.notification.*;

import static org.junit.Assert.*;

public class MultiNotificationParserTest extends AbstractNotificationParserTest {

	@Test
	public void selectText() throws Exception {
		TextNotificationParser textParser = new TextNotificationParser(getPreferencesProvider());
		ProtobufNotificationParser protobufParser = new ProtobufNotificationParser(getPreferencesProvider());
		MultiNotificationParser parser = new MultiNotificationParser(null, textParser, protobufParser);
		
		byte[] data = createTextNotification().getBytes(TextNotificationParser.CHARSET);
		byte[] result = Arrays.copyOf(data, data.length + 1);
		result[data.length] = 0;
		Notification notification = parser.parse(result);
		Notification expectedNotification = createNotification();

		assertEquals(expectedNotification, notification);
	}

}
