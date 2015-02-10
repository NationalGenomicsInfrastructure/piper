package molmed.apps

import org.slf4j.{Logger, LoggerFactory}


/**
 * Created by vql on 23/01/15.
 */

class TestLogger(var t:Int, var oldT: Int ) {
  def setTemp(temp: Int) {
    oldT = t
    t = temp
    logger.debug("Temperature set to {}. Old temp was {}.", t, oldT)
  }

  private[apps] final val logger: Logger = LoggerFactory.getLogger(classOf[TestLogger])
}

object MyLoggers extends App{
        //val logger = LoggerFactory.getLogger(MyLoggers.getClass)
        val ts = new TestLogger(1, 50);
        ts.setTemp(1)
        ts.setTemp(55)
        println(ts.t)
        }

