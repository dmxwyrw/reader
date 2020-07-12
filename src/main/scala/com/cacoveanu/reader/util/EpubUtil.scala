package com.cacoveanu.reader.util

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.file.Paths
import java.util.zip.ZipFile

import com.cacoveanu.reader.entity.{Content, Section, TocEntry}
import com.cacoveanu.reader.service.xml.ResilientXmlLoader
import org.apache.tomcat.util.http.fileupload.IOUtils
import org.slf4j.{Logger, LoggerFactory}
import com.cacoveanu.reader.util.HtmlUtil.AugmentedHtmlString
import com.cacoveanu.reader.util.HtmlUtil.AugmentedJsoupDocument

import scala.jdk.CollectionConverters._
import scala.xml.{Elem, XML}

object EpubUtil {

  private val log: Logger = LoggerFactory.getLogger(EpubUtil.getClass)
  private val OPF_REGEX = ".+\\.opf$"
  private val NCX_REGEX = ".+\\.ncx$"

  def readResource(epubPath: String, resourcePath: String): Option[Array[Byte]] = {
    val basePath = baseLink(resourcePath)
    var zipFile: ZipFile = null

    try {
      zipFile = new ZipFile(epubPath)
      zipFile.entries().asScala.find(e => e.getName == basePath).map(f => {
        val fileContents = zipFile.getInputStream(f)
        val bos = new ByteArrayOutputStream()
        IOUtils.copy(fileContents, bos)
        bos.toByteArray
      })
    } catch {
      case e: Throwable =>
        log.error(s"failed to read epub $epubPath", e)
        None
    } finally {
      if (zipFile != null) zipFile.close()
    }
  }

  def findResource(epubPath: String, resourceRegex: String): Option[String] = {
    var zipFile: ZipFile = null
    val pattern = resourceRegex.r

    try {
      zipFile = new ZipFile(epubPath)
      zipFile.entries().asScala
        .find(e => pattern.pattern.matcher(e.getName.toLowerCase).matches)
        .map(f => f.getName)
    } catch {
      case e: Throwable =>
        log.error(s"failed to read epub $epubPath", e)
        None
    } finally {
      if (zipFile != null) zipFile.close()
    }
  }

  private def getOpf(epubPath: String): Option[(String, Elem)] =
    findResource(epubPath, OPF_REGEX) match {
      case Some(opfPath) =>
        readResource(epubPath, opfPath).flatMap(getXml) match {
          case Some(xml) => Some((opfPath, xml))
          case None => None
        }
      case None => None
    }

  private def getNcx(epubPath: String) =
    findResource(epubPath, NCX_REGEX) match {
      case Some(ncxPath) =>
        readResource(epubPath, ncxPath).flatMap(getXml) match {
          case Some(xml) => Some((ncxPath, xml))
          case None => None
        }
      case None => None
    }

  private def getSectionSize(epubPath: String, sectionPath: String) = {
    if (FileUtil.getExtension(EpubUtil.baseLink(sectionPath)) == "html") {
      EpubUtil.readResource(epubPath, EpubUtil.baseLink(sectionPath))
        //.flatMap(getXml)
        .map(bytes => new String(bytes, "UTF-8"))
        .map(text => text.asHtml.bodyText.length)
        //.map(html => (html \ "body").text.length)
        .getOrElse(-1)
    } else {
      1
    }
  }

  def getToc(epubPath: String) = {
    getNcx(epubPath).map { case (opfPath, opf) =>
      (opf \ "navMap" \ "navPoint")
        .map(n => new TocEntry(
          (n \ "@playOrder").text.toInt,
          (n \ "navLabel" \ "text").text,
          getAbsoluteEpubPath(opfPath, (n \ "content" \ "@src").text)
        ))
    }.getOrElse(Seq())
  }

  def getSections(epubPath: String, toc: Seq[TocEntry]) = {
    val essentialToc = if (toc.size > 1) {
      Seq(toc(0)) ++ toc.sliding(2)
        .filter(p => EpubUtil.baseLink(p(0).link) != EpubUtil.baseLink(p(1).link))
        .map(p => p(1))
    } else toc

    var totalSize = 0
    val sections = essentialToc.map(e => {
      val basePath = baseLink(e.link)
      val sectionSize = getSectionSize(epubPath, basePath)
      val section = new Section(e.index, basePath, totalSize, sectionSize)
      totalSize = totalSize + sectionSize
      section
    })
    sections
  }

  // todo: rethink much of this and make book scanning more resilient to issues
  // todo: TOC is different from resources list, multiple TOC entries may be in a single resource
  /*def getToc(epubPath: String) = {
    val toc = getNcx(epubPath).map { case (opfPath, opf) =>
      (opf \ "navMap" \ "navPoint")
        .map(n => new Section(
          (n \ "@playOrder").text.toInt,
          (n \ "navLabel" \ "text").text,
          getAbsoluteEpubPath(opfPath, (n \ "content" \ "@src").text),
          -1,
          -1
        ))
    }.getOrElse(Seq())
    // remove parts of toc that are in the same file
    if (toc.size > 1) {
      val realToc = Seq(toc(0)) ++
        toc.sliding(2)
          .filter(p => EpubUtil.baseLink(p(0).link) != EpubUtil.baseLink(p(1).link))
          .map(p => p(1))
      var totalSize = 0
      val realTocWithSizes = realToc.map(e => {
        val sectionSize = getSectionSize(epubPath, e.link)
        val ne = new Section(e.index, e.title, e.link, totalSize, sectionSize)
        totalSize = totalSize + sectionSize
        ne
      })
      realTocWithSizes
    } else toc
  }*/

  def baseLink(link: String): String =
    if (link.indexOf("#") >= 0) link.substring(0, link.indexOf("#"))
    else link

  def getTitle(epubPath: String): Option[String] =
    getOpf(epubPath).flatMap { case (_, opf) => (opf \ "metadata" \ "title").headOption.map(_.text) }

  def getAuthor(epubPath: String): Option[String] =
    getOpf(epubPath).flatMap { case (_, opf) => (opf \ "metadata" \ "creator").headOption.map(_.text) }

  def getTocLink(epubPath: String): Option[String] =
    getOpf(epubPath) match {
      case Some((opfPath, opf)) =>
        (opf \ "guide" \ "reference")
          .find(n => (n \ "@type").text == "toc")
          .map(n => (n \ "@href").text)
          .map(link => getAbsoluteEpubPath(opfPath, link))
      case None => None
    }

  private def getCoverResource(opfPath: String, contentOpf: Elem): Option[(String, String)] = {
    val coverId = (contentOpf \ "metadata" \ "meta")
      .find(n => (n \ "@name").text == "cover")
      .map(n => (n \ "@content").text)

    val coverResource = coverId.flatMap(id => {
        (contentOpf \\ "manifest" \ "item")
          .find(node => (node \ "@id").text == id )
          .map(node => (
            getAbsoluteEpubPath(opfPath, (node \ "@href").text),
            (node \ "@media-type").text)
          )
      })
    coverResource
  }

  def getCover(epubPath: String): Option[Content] =
    getOpf(epubPath).flatMap { case (opfPath, opf) => getCoverResource(opfPath, opf)}
    .flatMap { case (href, contentType) =>
      readResource(epubPath, href)
        .map(bytes => Content(None, contentType, bytes))
    }

  def getAbsoluteEpubPath(povPath: String, currentPath: String): String = {
    // check if current path is absolute
    if (currentPath.startsWith("/")) return currentPath
    // get the folder path of the povPath
    val folderPath = if (povPath.lastIndexOf("/") >= 0) povPath.substring(0, povPath.lastIndexOf("/"))
    val newPath = folderPath + "/" + currentPath
    val normalizedPath = Paths.get(newPath).normalize().toString.replaceAll("\\\\", "/")
    normalizedPath
  }

  def getXml(data: Array[Byte]) = {
    try {
      Some(ResilientXmlLoader.load(new ByteArrayInputStream(data)))
    } catch {
      case t: Throwable =>
        log.warn(s"failed to parse xml", t)
        None
    }
  }
}
