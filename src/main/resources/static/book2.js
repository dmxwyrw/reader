function scrollNecessary(el) {
    var sn = el.scrollHeight > el.offsetHeight || el.scrollWidth > el.offsetWidth
    console.log("scroll necessary: " + sn)
    return sn
}

function getZoom() {
    if (document.bookZoom) {
        return document.bookZoom
    } else {
        var zoom = parseFloat(getMeta("bookZoom"))
        document.bookZoom = zoom
        return document.bookZoom
    }
}

function getPagesKey() {
    return getMeta("bookId") + "_" + getViewportWidth() + "_" + getViewportHeight() + "_" + getZoom()
}

function getPageFor(position) {
    var pagesKey = getPagesKey()
    //var savedPages = window.localStorage.getItem(pagesKey)
    var savedPages = document.savedPages
    if (savedPages != null) {
        // search for page
        for (var i = 0; i < savedPages.length; i++) {
            if (savedPages[i].start <= position && position <= savedPages[i].end) {
                // we found the page
                return savedPages[i]
            }
        }
    }
    // no page available
    return null
}

function getContentFor(start, end, callback) {
    if (document.section != null && document.section.start <= start && start <= end && end <= document.section.end) {
        console.log("we have the content")
        if (callback != null) {
            callback(document.section.copy(start, end).getContent())
        }
    } else {
        console.log("we need to download the content")
        // download section
        downloadSection(start, function(section) {
            console.log("we have now downloaded the content")
            document.section = section
            console.log("we have saved the book section")
            if (document.section.start <= start && start <= end && end <= document.section.end) {
                console.log("start stop check passed, invoking the callback")
                if (callback != null) {
                    console.log("callback is good, invoking now")
                    callback(document.section.copy(start, end).getContent())
                }
            }
        })
    }
}

function displayPageFor(position, firstTry = true) {
    console.log("displaying page " + position)
    showSpinner()
    var page = getPageFor(position)
    if (page == null) {
        // compute pages for section and retry
        if (firstTry) {
            computePagesForSection(position)
        }
        window.setTimeout(function() {
            displayPageFor(position, false)
        }, 100)
    } else {
        console.log("found page for " + position)
        getContentFor(page.start, page.end, function(text) {
            var content = document.getElementById("ch_content")
            content.innerHTML = text
            document.currentPage = page
            hideSpinner()
        })
    }
}

function nextPage() {
    if (document.currentPage != null) {
        if (document.currentPage.end < parseInt(getMeta("bookEnd"))) {
            displayPageFor(document.currentPage.end + 1)
        }
    }
}
function previousPage() {
    if (document.currentPage != null) {
        if (document.currentPage.start > parseInt(getMeta("bookStart"))) {
            displayPageFor(document.currentPage.start - 1)
        }
    }
}

function handleResize() {
    fixComponentHeights()
    if (document.currentPage != null) {
        var position = document.currentPage.start
        document.savedPages = []
        document.currentPage = null
        var content = document.getElementById("ch_content")
        content.innerHTML = ""
        displayPageFor(position)
    }
}

function downloadSection(position, callback) {
    var xhttp = new XMLHttpRequest()
    xhttp.onreadystatechange = function() {
        if (this.readyState == 4 && this.status == 200) {
            var jsonObj = JSON.parse(this.responseText)
            var node = convert(jsonObj)
            document.getElementById("ch_load_buffer").innerHTML = node.getContent()
            if (callback != null) {
                callback(node)
            }
        }
    }
    xhttp.open("GET", "bookSection?id=" + getMeta("bookId") + "&position=" + position, true)
    xhttp.send()
}

function getSectionFor(position) {
    if (document.section != null && document.section.start <= position && position <= document.section.end) {
        return document.section
    } else {
        downloadSection(position, function(node) {
            document.section = node
        })
        return null
    }
}

function computePagesForSection(position) {
    downloadSection(position, function(section) {
        window.setTimeout(function() {
            compute(section, section.start)
        }, 100)
    })
}

function compute(section, start) {
    console.log("computing pages for section " + section.start + " position " + start)
    var shadowContent = document.getElementById("ch_shadow_content")
    shadowContent.innerHTML = ""

    var previousEnd = start
    var end = section.findSpaceAfter(start)
    console.log("trying end " + end)
    shadowContent.innerHTML = section.copy(start, end).getContent()
    while (scrollNecessary(shadowContent) == false && end < section.end) {
        previousEnd = end
        end = section.findSpaceAfter(end)
        console.log("trying end " + end)
        shadowContent.innerHTML = section.copy(start, end).getContent()
    }
    if (end < section.end) {
        end = previousEnd
    }

    // store page
    if (document.savedPages == null) {
        document.savedPages = []
    }
    console.log("going with end " + end)
    document.savedPages.push({start: start, end: end})

    if (end < section.end) {
        // schedule computation for the next page
        window.setTimeout(function() {
            compute(section, end + 1)
        }, 100)
    }
}

function showSpinner() {
    var spinner = document.getElementById("spinner")
    spinner.style.visibility = "visible"
}

function hideSpinner() {
    var spinner = document.getElementById("spinner")
    spinner.style.visibility = "hidden"
}

function fixComponentHeights() {
    var height = getViewportHeight()
    var contentTop = height * .05
    var contentHeight = height * .9
    document.getElementById("ch_content").style.top = contentTop + "px"
    document.getElementById("ch_content").style.height = contentHeight + "px"
    document.getElementById("ch_shadow_content").style.top = contentTop + "px"
    document.getElementById("ch_shadow_content").style.height = contentHeight + "px"

    var pageControlsTop = 0
    var pageControlsHeight = height * .9
    document.getElementById("ch_prev").style.top = pageControlsTop + "px"
    document.getElementById("ch_prev").style.height = pageControlsHeight + "px"
    document.getElementById("ch_next").style.top = pageControlsTop + "px"
    document.getElementById("ch_tools_right").style.height = pageControlsHeight + "px"

    var toolsControlsHeight = height - pageControlsHeight
    document.getElementById("ch_tools_left").style.height = toolsControlsHeight + "px"
    document.getElementById("ch_tools_right").style.height = toolsControlsHeight + "px"
}

window.onload = function() {
    // fix viewport height
    fixComponentHeights()


    // other page controls heights need to be fixed like this too


    enableGesturesOnElement(document.getElementById("ch_prev"), {
        "clickAction": (x, y) => previousPage()
    })
    enableGesturesOnElement(document.getElementById("ch_next"), {
        "clickAction": (x, y) => nextPage()
    })

    var startPosition = num(getMeta("startPosition"))
    console.log("start position: " + startPosition)

    displayPageFor(parseInt(getMeta("startPosition")))
}