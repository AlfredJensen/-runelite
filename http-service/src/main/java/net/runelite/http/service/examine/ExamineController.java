/*
 * Copyright (c) 2017, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.http.service.examine;

import static net.runelite.http.service.examine.ExamineType.ITEM;
import static net.runelite.http.service.examine.ExamineType.NPC;
import static net.runelite.http.service.examine.ExamineType.OBJECT;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import static org.springframework.web.bind.annotation.RequestMethod.POST;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/examine")
public class ExamineController
{
	private final ExamineService examineService;

	@Autowired
	public ExamineController(ExamineService examineService)
	{
		this.examineService = examineService;
	}

	@RequestMapping("/npc/{id}")
	public String getNpc(@PathVariable int id)
	{
		return examineService.get(NPC, id);
	}

	@RequestMapping("/object/{id}")
	public String getObject(@PathVariable int id)
	{
		return examineService.get(OBJECT, id);
	}

	@RequestMapping("/item/{id}")
	public String getItem(@PathVariable int id)
	{
		return examineService.get(ITEM, id);
	}

	@RequestMapping(path = "/npc/{id}", method = POST)
	public void submitNpc(@PathVariable int id, @RequestBody String examine)
	{
		examineService.insert(NPC, id, examine);
	}

	@RequestMapping(path = "/object/{id}", method = POST)
	public void submitObject(@PathVariable int id, @RequestBody String examine)
	{
		examineService.insert(OBJECT, id, examine);
	}

	@RequestMapping(path = "/item/{id}", method = POST)
	public void submitItem(@PathVariable int id, @RequestBody String examine)
	{
		examineService.insert(ITEM, id, examine);
	}
}
