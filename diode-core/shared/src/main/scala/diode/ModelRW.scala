package diode

import scala.language.higherKinds

/**
  * Typeclass for fast equality comparison.
  */
trait FastEq[A] {
  def eqv(a: A, b: A): Boolean
}

/**
  * Base trait for all model readers
  *
  * @tparam M Type of the base model
  * @tparam S Type of the reader value
  */
trait ModelR[M, S] {
  /**
    * Returns the value of the reader
    */
  def value: S

  /**
    * Returns the value of the reader
    */
  def apply(): S = value

  /**
    * Evaluates the reader against a supplied `model`
    */
  def eval(model: M): S

  /**
    * Checks if `that` is equal to `this` using an appropriate equality check
    *
    * @param that Value to compare with
    * @return
    */
  def ===(that: S): Boolean

  def =!=(that: S): Boolean = ! ===(that)

  /**
    * Zooms into the model using the provided accessor function
    *
    * @param get Function to go from current reader to a new value
    */
  def zoom[T](get: S => T)(implicit feq: FastEq[_ >: T]): ModelR[M, T]

  /**
    * Maps over current reader into a new value provided by `f`. Reader type `S` must be of type `F[A]`,
    * for example `Option[A]`.
    *
    * @param f The function to apply
    */
  def map[F[_] <: AnyRef, A, B](f: A => B)
    (implicit ev: S =:= F[A], monad: Monad[F], feq: FastEq[_ >: B]): ModelR[M, F[B]] =
    zoomMap((_: S) => ev(value))(f)

  /**
    * FlatMaps over current reader into a new value provided by `f`. Reader type `S` must be of type `F[A]`,
    * for example `Option[A]`.
    *
    * @param f The function to apply, must return a value of type `F[B]`
    */
  def flatMap[F[_] <: AnyRef, A, B](f: A => F[B])
    (implicit ev: S =:= F[A], monad: Monad[F], feq: FastEq[_ >: B]): ModelR[M, F[B]] =
    zoomFlatMap((_: S) => ev(value))(f)

  /**
    * Zooms into the model and maps over the zoomed value, which must be of type `F[A]`
    *
    * @param fa Zooming function
    * @param f  The function to apply
    */
  def zoomMap[F[_] <: AnyRef, A, B](fa: S => F[A])(f: A => B)
    (implicit monad: Monad[F], feq: FastEq[_ >: B]): ModelR[M, F[B]]

  /**
    * Zooms into the model and flatMaps over the zoomed value, which must be of type `F[A]`
    *
    * @param fa Zooming function
    * @param f  The function to apply, must return a value of type `F[B]`
    */
  def zoomFlatMap[F[_] <: AnyRef, A, B](fa: S => F[A])(f: A => F[B])
    (implicit monad: Monad[F], feq: FastEq[_ >: B]): ModelR[M, F[B]]

  /**
    * Combines this reader with another reader to provide a new reader returning a tuple of the values
    * of the two original readers.
    *
    * @param that The other reader
    */
  def zip[SS](that: ModelR[M, SS])(implicit feqS: FastEq[_ >: S], feqSS: FastEq[_ >: SS]): ModelR[M, (S, SS)]
}

/**
  * Base trait for all model writers
  *
  * @tparam M Type of the base model
  * @tparam S Type of the reader/writer value
  */
trait ModelRW[M, S] extends ModelR[M, S] {
  /**
    * Updatews the model using the value provided and returns the updated model.
    */
  def updated(newValue: S): M

  /**
    * Zooms into the model using the provided `get` function. The `set` function is used to
    * update the model with a new value.
    *
    * @param get Function to go from current reader to a new value
    * @param set Function to update the model with a new value
    */
  def zoomRW[T](get: S => T)(set: (S, T) => S)(implicit feq: FastEq[_ >: T]): ModelRW[M, T]

  /**
    * Zooms into the model and maps over the zoomed value, which must be of type `F[A]`. The `set` function is used to
    * update the model with a new value.
    *
    * @param fa  Zooming function
    * @param f   The function to apply
    * @param set Function to update the model with a new value
    */
  def zoomMapRW[F[_] <: AnyRef, A, B](fa: S => F[A])(f: A => B)(set: (S, F[B]) => S)
    (implicit monad: Monad[F], feq: FastEq[_ >: B]): ModelRW[M, F[B]]

  /**
    * Zooms into the model and flatMaps over the zoomed value, which must be of type `F[A]`. The `set` function is used to
    * update the model with a new value.
    *
    * @param fa  Zooming function
    * @param f   The function to apply
    * @param set Function to update the model with a new value
    */
  def zoomFlatMapRW[F[_] <: AnyRef, A, B](fa: S => F[A])(f: A => F[B])(set: (S, F[B]) => S)
    (implicit monad: Monad[F], feq: FastEq[_ >: B]): ModelRW[M, F[B]]
}

/**
  * Implements common functionality for all model readers
  *
  * @tparam M Type of the base model
  * @tparam S Type of the reader value
  */
trait BaseModelR[M, S] extends ModelR[M, S] {
  protected def root: ModelR[M, M]

  protected def getF(model: M): S

  override def value = getF(root.value)

  override def eval(model: M) = getF(model)

  override def zoom[T](get: S => T)(implicit feq: FastEq[_ >: T]) =
    new ZoomModelR[M, T](root, get compose this.getF)

  override def zip[SS](that: ModelR[M, SS])(implicit feqS: FastEq[_ >: S], feqSS: FastEq[_ >: SS]) =
    new ZipModelR[M, S, SS](root, eval, that.eval)

  override def zoomMap[F[_] <: AnyRef, A, B](fa: S => F[A])(f: A => B)
    (implicit monad: Monad[F], feq: FastEq[_ >: B]): ModelR[M, F[B]] =
    new MapModelR(root, fa compose getF, f)

  override def zoomFlatMap[F[_] <: AnyRef, A, B](fa: S => F[A])(f: A => F[B])
    (implicit monad: Monad[F], feq: FastEq[_ >: B]): ModelR[M, F[B]] =
    new FlatMapModelR(root, fa compose getF, f)
}

/**
  * Model reader for the root value
  */
class RootModelR[M <: AnyRef](get: => M) extends BaseModelR[M, M] {
  protected def root = this

  protected def getF(model: M) = get

  override def value = get

  override def eval(model: M) = get

  override def ===(that: M): Boolean = this eq that
}

/**
  * Model reader for a zoomed value
  */
class ZoomModelR[M, S](protected val root: ModelR[M, M], get: M => S)
  (implicit feq: FastEq[_ >: S]) extends BaseModelR[M, S] {
  protected def getF(model: M) = get(model)

  override def ===(that: S): Boolean = feq.eqv(value, that)
}

trait MappedModelR[F[_], M, B] {
  protected def monad: Monad[F]
  protected def feq: FastEq[_ >: B]
  protected def mapValue: F[B]

  private var memoized = mapValue

  protected def getF(model: M): F[B] = {
    val v = mapValue
    // update memoized value only when the value inside the monad changes
    if (!monad.isEqual(v, memoized)(feq.eqv)) {
      memoized = v
    }
    memoized
  }
}

/**
  * Model reader for a mapped value
  */
class MapModelR[F[_] <: AnyRef, M, A, B](protected val root: ModelR[M, M], get: M => F[A], f: A => B)
  (implicit val monad: Monad[F], val feq: FastEq[_ >: B])
  extends BaseModelR[M, F[B]] with MappedModelR[F, M, B] {

  override protected def mapValue = monad.map(get(root.value))(f)

  override def ===(that: F[B]) = monad.isEqual(value, that)(feq.eqv)
}

/**
  * Model reader for a flatMapped value
  */
class FlatMapModelR[F[_] <: AnyRef, M, A, B](protected val root: ModelR[M, M], get: M => F[A], f: A => F[B])
  (implicit val monad: Monad[F], val feq: FastEq[_ >: B])
  extends BaseModelR[M, F[B]] with MappedModelR[F, M, B] {

  override protected def mapValue = monad.flatMap(get(root.value))(f)

  override def ===(that: F[B]) = monad.isEqual(value, that)(feq.eqv)
}

/**
  * Model reader for two zipped readers
  */
class ZipModelR[M, S, SS](protected val root: ModelR[M, M], get1: M => S, get2: M => SS)
  (implicit feqS: FastEq[_ >: S], feqSS: FastEq[_ >: SS])
  extends BaseModelR[M, (S, SS)] {
  // initial value for zipped
  private var zipped = (get1(root.value), get2(root.value))

  // ZipModel uses optimized `get` functions to check if the contents of the tuple has changed or not
  // Different comparison functions are used for boxed values and real references
  protected def getF(model: M) = {
    // check if inner references have changed
    val v1 = get1(root.value)
    val v2 = get2(root.value)
    if (!feqS.eqv(zipped._1, v1) || !feqSS.eqv(zipped._2, v2)) {
      // create a new tuple
      zipped = (v1, v2)
    }
    zipped
  }

  override def ===(that: (S, SS)) = zipped eq that
}

/**
  * Implements common functionality for all reader/writers
  *
  * @tparam M Type of the base model
  * @tparam S Type of the reader/writer value
  */
trait BaseModelRW[M, S] extends ModelRW[M, S] with BaseModelR[M, S] {
  protected def setF(model: M, value: S): M

  override def zoomRW[U](get: S => U)(set: (S, U) => S)(implicit feq: FastEq[_ >: U]) =
    new ZoomModelRW[M, U](root, get compose getF, (s, u) => setF(s, set(getF(s), u)))

  override def zoomMapRW[F[_] <: AnyRef, A, B](fa: S => F[A])(f: A => B)(set: (S, F[B]) => S)
    (implicit monad: Monad[F], feq: FastEq[_ >: B]) =
    new MapModelRW(root, fa compose getF, f)((s, u) => setF(s, set(getF(s), u)))

  override def zoomFlatMapRW[F[_] <: AnyRef, A, B](fa: S => F[A])(f: A => F[B])(set: (S, F[B]) => S)
    (implicit monad: Monad[F], feq: FastEq[_ >: B]) =
    new FlatMapModelRW(root, fa compose getF, f)((s, u) => setF(s, set(getF(s), u)))

  override def updated(newValue: S) = setF(root.value, newValue)
}

/**
  * Model reader/writer for the root value
  */
class RootModelRW[M <: AnyRef](get: => M) extends RootModelR(get) with BaseModelRW[M, M] {
  protected override def setF(model: M, value: M) = value

  // override for root because it's a simpler case
  override def zoomRW[T](get: M => T)(set: (M, T) => M)(implicit feq: FastEq[_ >: T]) =
    new ZoomModelRW[M, T](this, get, (s, u) => set(value, u))
}

/**
  * Model reader/writer for a zoomed value
  */
class ZoomModelRW[M, S](root: ModelR[M, M], get: M => S, set: (M, S) => M)
  (implicit feq: FastEq[_ >: S]) extends ZoomModelR(root, get) with BaseModelRW[M, S] {
  protected override def setF(model: M, value: S) = set(model, value)
}

/**
  * Model reader/writer for a mapped value
  */
class MapModelRW[F[_] <: AnyRef, M, A, B](root: ModelR[M, M], get: M => F[A], f: A => B)(set: (M, F[B]) => M)
  (implicit monad: Monad[F], feq: FastEq[_ >: B])
  extends MapModelR(root, get, f) with BaseModelRW[M, F[B]] {
  protected override def setF(model: M, value: F[B]) = set(model, value)
}

/**
  * Model reader/writer for a flatMapped value
  */
class FlatMapModelRW[F[_] <: AnyRef, M, A, B](root: ModelR[M, M], get: M => F[A], f: A => F[B])(set: (M, F[B]) => M)
  (implicit monad: Monad[F], feq: FastEq[_ >: B])
  extends FlatMapModelR(root, get, f) with BaseModelRW[M, F[B]] {
  protected override def setF(model: M, value: F[B]) = set(model, value)
}

/**
  * Marker trait for readers to indicate the use of value equality instead of the default reference equality
  */
trait UseValueEq

/**
  * Implicit FastEq typeclass instances for AnyRef and AnyVal
  */
trait FastEqLowPri {

  // for any reference type, use reference equality check
  implicit object AnyRefEq extends FastEq[AnyRef] {
    override def eqv(a: AnyRef, b: AnyRef): Boolean = a eq b
  }

  // for any value type, use normal equality check
  implicit object AnyValEq extends FastEq[AnyVal] {
    override def eqv(a: AnyVal, b: AnyVal): Boolean = a == b
  }

  object ValueEq extends FastEq[Any] {
    override def eqv(a: Any, b: Any): Boolean = a == b
  }

}

/**
  * Implicit FastEq typeclass instance for `UseValueEq` marker trait
  */
object FastEq extends FastEqLowPri {

  // for classes extending marker trait `UseValueEq`, use normal equality check
  implicit object markerEq extends FastEq[UseValueEq] {
    override def eqv(a: UseValueEq, b: UseValueEq): Boolean = a == b
  }

}
