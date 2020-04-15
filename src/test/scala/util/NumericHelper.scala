package util

import arithmetic.Sigmoid

import scala.math.exp

object NumericHelper {

  def getSigmoidRange = Sigmoid.getRange

  def refSigmoid(x: Double) = 1 / (1 + exp(-x))

  def refReLU(x: Double) = if (x > 0.0) { if (x < 1.0) x else 0.999 } else 0.0

  def fixedToDouble(n: Int, scale: Int) = n.toDouble / scale

  def doubleToFixed(x: Double, scale: Int) = (x * scale).toInt

}
