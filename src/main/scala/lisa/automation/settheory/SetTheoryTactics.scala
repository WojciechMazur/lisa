package lisa.automation.settheory

import lisa.automation.kernel.SimplePropositionalSolver.*
import lisa.automation.kernel.SimpleSimplifier.*
import lisa.kernel.proof.SequentCalculus as SC
import lisa.mathematics.SetTheory
import lisa.prooflib.BasicStepTactic.*
import lisa.prooflib.Library
import lisa.prooflib.ProofTacticLib.{_, given}
import lisa.prooflib.*
import lisa.settheory.SetTheoryLibrary
import lisa.utils.KernelHelpers.*
import lisa.utils.Printer

object SetTheoryTactics {

  import lisa.settheory.SetTheoryLibrary.{_, given}
  // var defs
  private val x = variable
  private val y = variable
  private val z = variable
  private val schemPred = predicate(1)

  /**
   * Deduced Tactic --- Unique Comprehension
   *
   * Generates a unique existence proof. Given a set `x`, and a predicate `P(t,
   * x)`, comprehension postulates there is a set containing the elements `t` of
   * `x` satisfying `P(t, x)`, denoted `{t ∈ x | P(t, x)}`. This set is unique
   * by extensionality.
   *
   *    `() ⊢ ∃! z. ∀ t. t ∈ z ⇔ (t ∈ x ⋀ P(t, x))`
   *
   * @param originalSet the set to apply comprehension on
   * @param separationPredicate the predicate to use for comprehension `(Term =>
   * Term => Boolean)`
   * @return subproof for unique existence of the set defined by inputs
   *
   * @example
   * Generates a subproof for the unique existence of the set `{t ∈ x | t ∈ y}`:
   * {{{
   *    have(() |- existsOne(z, in(t, z) <=> (in(t, x) /\ in(t, y)))) by uniqueComprehension(x, lambda(Seq(t, x), in(t, y)))
   * }}}
   * See [[setIntersection]] or [[relationDomain]] for more usage.
   */
  object UniqueComprehension extends ProofTactic {
    def apply(using proof: SetTheoryLibrary.Proof, line: sourcecode.Line, file: sourcecode.File, om: OutputManager)(originalSet: Term, separationPredicate: LambdaTermFormula)(
        bot: Sequent
    ): proof.ProofTacticJudgement = {
      require(separationPredicate.vars.length == 2) // separationPredicate takes two args
      given SetTheoryLibrary.type = SetTheoryLibrary
      // fresh variable names to avoid conflicts
      val t1 = VariableLabel(freshId(separationPredicate.body.freeVariables.map(_.id) ++ originalSet.freeVariables.map(_.id), x.id))
      val t2 = VariableLabel(freshId(separationPredicate.body.freeVariables.map(_.id) ++ originalSet.freeVariables.map(_.id), y.id))

      val prop = (in(t2, originalSet) /\ separationPredicate(Seq(t2, originalSet)))
      def fprop(z: Term) = forall(t2, in(t2, z) <=> prop)

      /**
       * Proof Summary:
       *
       * originalSet = x
       * separationPredicate = \t x -> P(t, x)
       *
       * have    () |- ∃! z. t ∈ z <=> (t ∈ x /\ P(t, x))                                    Comprehension Schema Instantiation
       * import  ∃ z. t ∈ z <=> (t ∈ x /\ P(t, x)) |- ∃! z. t ∈ z <=> (t ∈ x /\ P(t, x))     Unique by Extension [[uniqueByExtension]] Instantiation
       * have    () |- ∃! z. t ∈ z <=> (t ∈ x /\ P(t, x))                                    Cut
       */
      val sp = SUBPROOF(using proof)(Some(bot)) { // TODO check if isInstanceOf first
        have(() |- exists(t1, forall(t2, in(t2, t1) <=> (in(t2, z) /\ sPhi(t2, z))))) by Rewrite(comprehensionSchema)
        thenHave(() |- exists(t1, forall(t2, in(t2, t1) <=> (in(t2, originalSet) /\ sPhi(t2, originalSet))))) by InstFunSchema(Map(z -> originalSet))
        val existence = thenHave(() |- exists(t1, fprop(t1))) by InstPredSchema(Map(sPhi -> lambda(Seq(t1, t2), separationPredicate(Seq(t1, t2)))))

        val existsToUnique = have(exists(t1, fprop(t1)) |- existsOne(t1, fprop(t1))) by InstPredSchema(Map(schemPred -> (t2, prop)))(SetTheory.uniqueByExtension)

        // assumption elimination
        have(() |- existsOne(t1, fprop(t1))) by Cut(existence, existsToUnique)
      }

      // safely check, unwrap, and return the proof judgement
      unwrapTactic(sp.judgement.asInstanceOf[proof.ProofTacticJudgement])("Subproof for unique comprehension failed.")
    }
  }

}
