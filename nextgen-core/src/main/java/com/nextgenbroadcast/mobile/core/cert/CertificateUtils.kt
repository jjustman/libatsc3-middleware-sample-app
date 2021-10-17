package com.nextgenbroadcast.mobile.core.cert

import com.nextgenbroadcast.mobile.core.LOG
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.bouncycastle.asn1.*
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.crypto.AsymmetricBlockCipher
import org.bouncycastle.crypto.digests.SHA1Digest
import org.bouncycastle.crypto.encodings.PKCS1Encoding
import org.bouncycastle.crypto.engines.RSAEngine
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters
import org.bouncycastle.crypto.params.RSAKeyParameters
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters
import java.io.*
import java.math.BigInteger
import java.security.*
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPrivateCrtKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.*

/*
    Could be helpful in feature
    https://gist.github.com/vivekkr12/c74f7ee08593a8c606ed96f4b62a208a
 */
object CertificateUtils {
    val TAG: String = CertificateUtils::class.java.simpleName

    const val CERTIFICATE_ALGORITHM = "X509"

    private const val KEY_ALGORITHM = "RSA"

    fun genCertificate(issuer: String): Pair<PrivateKey, java.security.cert.Certificate>? {
        try {
            // Create a new pair of RSA keys using BouncyCastle classes
            val keypair = RSAKeyPairGenerator().apply {
                init(RSAKeyGenerationParameters(BigInteger.valueOf(3), SecureRandom(), 1024, 80))
            }.generateKeyPair()
            val publicKey = keypair.public as RSAKeyParameters
            val privateKey = keypair.private as RSAPrivateCrtKeyParameters

            // We also need our pair of keys in another format, so we'll convert
            // them using java.security classes
            val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM)
            val pubKey = keyFactory.generatePublic(RSAPublicKeySpec(publicKey.modulus, publicKey.exponent))
            val privKey = keyFactory.generatePrivate(RSAPrivateCrtKeySpec(publicKey.modulus, publicKey.exponent,
                    privateKey.exponent, privateKey.p, privateKey.q, privateKey.dp, privateKey.dq, privateKey.qInv))

            // We have to sign our public key now. As we do not need or have
            // some kind of CA infrastructure, we are using our new keys
            // to sign themselves

            // Set certificate meta information
            val sigAlgId = AlgorithmIdentifier(PKCSObjectIdentifiers.sha1WithRSAEncryption, DERNull.INSTANCE)
            val x500Name = X500Name("CN=$issuer")
            val certGen = V3TBSCertificateGenerator().apply {
                setSerialNumber(ASN1Integer(System.currentTimeMillis()))
                setIssuer(x500Name)
                setSubject(x500Name)
                setSignature(sigAlgId)
                ASN1InputStream(ByteArrayInputStream(pubKey.encoded)).use { ais ->
                    setSubjectPublicKeyInfo(SubjectPublicKeyInfo.getInstance(ais.readObject()))
                }
            }

            // We want our keys to live long
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DATE, -1)
            certGen.setStartDate(Time(calendar.time))
            calendar.add(Calendar.YEAR, 10)
            certGen.setEndDate(Time(calendar.time))
            val tbsCert = certGen.generateTBSCertificate()

            // The signing: We first build a hash of our certificate, than sign
            // it with our private key
            val digester = SHA1Digest()
            val bOut = ByteArrayOutputStream()
            val dOut = DEROutputStream(bOut)
            dOut.writeObject(tbsCert)
            val certBlock = bOut.toByteArray()
            // first create digest
            digester.update(certBlock, 0, certBlock.size)
            val hash = ByteArray(digester.digestSize)
            digester.doFinal(hash, 0)
            // and sign that
            val rsa: AsymmetricBlockCipher = PKCS1Encoding(RSAEngine()).apply {
                init(true, privateKey)
            }
            val dInfo = DigestInfo(AlgorithmIdentifier(X509ObjectIdentifiers.id_SHA1, null), hash)
            val digest = dInfo.getEncoded("DER")
            val signature = rsa.processBlock(digest, 0, digest.size)
            dOut.close()

            // We build a certificate chain containing only one certificate
            val vector = ASN1EncodableVector().apply {
                add(tbsCert)
                add(sigAlgId)
                add(DERBitString(signature))
            }

            val certificate = DERSequence(vector).encoded.toX509Certificate()

            return Pair(privKey, certificate)
        } catch (e: Exception) {
            LOG.e(TAG, "Error on certificate creation: ", e)
        }

        return null
    }

    fun ByteArray.toX509Certificate(): X509Certificate {
        return inputStream().use { stream ->
            CertificateFactory.getInstance(CERTIFICATE_ALGORITHM).generateCertificate(stream) as X509Certificate
        }
    }

    fun ByteArray.toPrivateKey(): PrivateKey {
        return KeyFactory.getInstance(KEY_ALGORITHM).generatePrivate(PKCS8EncodedKeySpec(this))
    }

    fun X509Certificate.sha256Hash(): ByteString =
            publicKey.encoded.toByteString().sha256()

    fun java.security.cert.Certificate.publicHash(): String {
        require(this is X509Certificate) { "Public hash generation requires X509 certificates" }
        return this.sha256Hash().base64()
    }

    fun createKeyStore(certStream: InputStream, privateKeyStream: InputStream, keyPassword: String): KeyStore? {
        var certs: Collection<Certificate?>? = null
        var privateKey: PrivateKey? = null

        try {
            certs = BufferedInputStream(certStream).use { stream ->
                CertificateFactory.getInstance(CERTIFICATE_ALGORITHM).generateCertificates(stream)
            }
        } catch (e: IOException) {
            LOG.i(TAG, "Failed to load certificate file", e)
        } catch (e: CertificateException) {
            LOG.i(TAG, "Failed to load certificate file", e)
        }

        try {
            privateKey = DataInputStream(privateKeyStream).use { stream ->
                PrivateKeyReader.getPrivateKey(stream, KEY_ALGORITHM)
            }
        } catch (e: IOException) {
            LOG.i(TAG, "Failed to load private key from file", e)
        } catch (e: GeneralSecurityException) {
            LOG.i(TAG, "Failed to load private key from file", e)
        }

        if (certs != null && privateKey != null) {
            try {
                val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                    load(null)
                }

                keyStore.setKeyEntry("alias", privateKey, keyPassword.toCharArray(), certs.filterNotNull().toTypedArray())

                return keyStore
            } catch (e: KeyStoreException) {
                LOG.i(TAG, "Failed to create key store", e)
            } catch (e: NoSuchAlgorithmException) {
                LOG.i(TAG, "Failed to create key store", e)
            } catch (e: CertificateException) {
                LOG.i(TAG, "Failed to create key store", e)
            } catch (e: IOException) {
                LOG.i(TAG, "Failed to create key store", e)
            }
        }

        return null
    }

    // randomly generated key password for the key in the KeyStore
    fun generatePassword(): String = BigInteger(128, SecureRandom()).toString(32)
}