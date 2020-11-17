package com.cacoveanu.reader.controller

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

import com.cacoveanu.reader.entity.{Content, Setting}
import com.cacoveanu.reader.service.{BookService, ContentService, SettingService}
import com.cacoveanu.reader.util.{FileMediaTypes, FileTypes, FileUtil, HtmlUtil}
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.{MediaType, ResponseEntity}
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.{RequestMapping, RequestParam, ResponseBody}
import org.springframework.web.servlet.view.RedirectView
import com.cacoveanu.reader.util.HtmlUtil.AugmentedHtmlString
import com.cacoveanu.reader.util.HtmlUtil.AugmentedJsoupDocument

import scala.beans.BeanProperty
import scala.util.Random

@Controller
class BookController @Autowired()(private val contentService: ContentService,
                                  private val bookService: BookService,
                                  private val settingService: SettingService) {

  private def appendSettings(html: String): String = {
    val settings = Map("bookZoom" -> settingService.getSetting(Setting.BOOK_ZOOM))
    html.asHtml.addMeta(settings).asString
    //HtmlUtil.addMeta(html, settings)
  }

  private def toResponseEntity(content: Option[Content]) = content match {
    case Some(Content(_, FileMediaTypes.TEXT_HTML_VALUE, bytes)) =>
      ResponseEntity.ok().body(appendSettings(new String(bytes, "UTF-8")))

    case Some(Content(_, FileMediaTypes.TEXT_CSS_VALUE, bytes)) =>
      ResponseEntity.ok().body(new String(bytes, "UTF-8"))

    case Some(Content(_, FileMediaTypes.IMAGE_JPEG_VALUE, bytes)) =>
      ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(bytes)

    case Some(Content(_, FileMediaTypes.IMAGE_PNG_VALUE, bytes)) =>
      ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(bytes)

    case Some(Content(_, FileMediaTypes.IMAGE_GIF_VALUE, bytes)) =>
      ResponseEntity.ok().contentType(MediaType.IMAGE_GIF).body(bytes)

    case _ => ResponseEntity.notFound().build()
  }

  @RequestMapping(Array("/positions_test"))
  def getPositionsTest() = "positions_test"

  val positionsTestBookId = 8891
  val positionsTestBookPath = "text%2Fpart0005.html"

  /*@RequestMapping(Array("/positions_test_data"))
  @ResponseBody
  def getPositionsTestData() = {
    // book?id=8891&path=text%2Fpart0005.html&position=0
    val bks = bookService.loadBooks
    val i = Random.nextInt(bks.size)
    val b = bks(i)
    val si = Random.nextInt(b.getSections().size)
    val s = b.getSections()(si)

    //toResponseEntity(contentService.loadResource(positionsTestBookId, URLDecoder.decode(positionsTestBookPath, StandardCharsets.UTF_8.name())))
    val content = contentService.loadResource(b.id, URLDecoder.decode(s.link, StandardCharsets.UTF_8.name())).get
    val sizeWithoutSpaces = HtmlUtil.positionsTestGet(content.data)
    PositionsTestResponse(new String(content.data, "UTF-8"), sizeWithoutSpaces)
  }*/

  case class PositionsTestResponse(@BeanProperty content: String, @BeanProperty sizeWithoutSpaces: Int)

  @RequestMapping(Array("/positions_test_computed_data"))
  @ResponseBody
  def getPositionsTestComputedData() = {
    val content: Content = contentService.loadResource(positionsTestBookId, URLDecoder.decode(positionsTestBookPath, StandardCharsets.UTF_8.name())).get
    HtmlUtil.positionsTestGet(content.data)
  }

  @RequestMapping(Array("/book"))
  @ResponseBody
  def getBookResource(@RequestParam("id") bookId: java.lang.Long,
                      @RequestParam("path") path: String,
                      @RequestParam(name = "position", required = false) position: java.lang.Long) = {
    toResponseEntity(contentService.loadResource(bookId, URLDecoder.decode(path, StandardCharsets.UTF_8.name())))
  }

  @RequestMapping(Array("/openBook"))
  @ResponseBody
  def loadBook(@RequestParam("id") bookId: java.lang.Long) = {
    bookService.loadBook(bookId) match {
      case Some(book) => FileUtil.getExtension(book.path) match {
        case FileTypes.CBR =>
          new RedirectView(s"/comic?id=$bookId")

        case FileTypes.CBZ =>
          new RedirectView(s"/comic?id=$bookId")

        case FileTypes.PDF =>
          new RedirectView(s"/comic?id=$bookId")

        case FileTypes.EPUB => bookService.loadProgress(book) match {
          case Some(progress) => new RedirectView(s"/book?id=$bookId&path=${progress.section}&position=${progress.position}")
          //case None => new RedirectView(s"/book?id=$bookId&path=${book.getSections()(0).link}")
          case None => new RedirectView(s"/book?id=$bookId&path=${book.resources.get(0).path}")
        }
      }

      case _ => new RedirectView("/") // todo: maybe better to throw a not found
    }
  }

}
