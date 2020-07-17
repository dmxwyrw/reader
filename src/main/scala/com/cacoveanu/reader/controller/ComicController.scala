package com.cacoveanu.reader.controller

import com.cacoveanu.reader.service.{BookService, ContentService, UserService}
import com.cacoveanu.reader.util.WebUtil
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.{RequestMapping, RequestParam, ResponseBody}

@Controller
class ComicController @Autowired() (private val userService: UserService,
                                    private val contentService: ContentService,
                                    private val bookService: BookService) {

  @RequestMapping(Array("/comic"))
  def getComic(@RequestParam(name="id") id: java.lang.Long, model: Model): String = {
    bookService.loadBook(id) match {
      case Some(comic) =>
        val progress = bookService.loadProgress(comic)
        model.addAttribute("id", id)
        model.addAttribute("pages", comic.size)
        model.addAttribute("title", comic.title)
        model.addAttribute("collection", comic.collection)
        model.addAttribute("startPage", progress.map(p => p.position).getOrElse(0))
        "comic"
      case None => "" // todo: throw some error!
    }
  }

  @RequestMapping(Array("/imageData"))
  @ResponseBody
  def getImageData(@RequestParam("id") id: java.lang.Long, @RequestParam("page") page: Int): String = {
    contentService
      .loadResources(id, contentService.getBatchForPosition(page))
      .find(p => p.index.isDefined && p.index.get == page)
      .map(p => WebUtil.toBase64Image(p.mediaType, p.data))
      .getOrElse("")
    // todo: should throw a 404 if the right resource is not found!
  }

}
