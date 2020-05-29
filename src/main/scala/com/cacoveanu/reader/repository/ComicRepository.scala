package com.cacoveanu.reader.repository


import com.cacoveanu.reader.entity.DbComic
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.{JpaRepository, Query}
import org.springframework.data.repository.query.Param

trait ComicRepository extends JpaRepository[DbComic, java.lang.Long] {

  def findAllByOrderByCollectionAsc(pageable: Pageable): java.util.List[DbComic]

  @Query(value="select * from db_comic c where lower(c.title) like %:term% or lower(c.collection) like %:term%", nativeQuery = true)
  def search(@Param("term") term: String): java.util.List[DbComic]
}
