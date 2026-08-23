import { NextAuthOptions, Session, User } from "next-auth";
import GoogleProvider from "next-auth/providers/google";
import CredentialsProvider from "next-auth/providers/credentials";
import { JWT } from "next-auth/jwt";
import { AdapterUser } from "next-auth/adapters";

export const authOptions: NextAuthOptions = {
    providers: [
        ...(process.env.GOOGLE_CLIENT_ID && process.env.GOOGLE_CLIENT_SECRET
            ? [
                GoogleProvider({
                    clientId: process.env.GOOGLE_CLIENT_ID,
                    clientSecret: process.env.GOOGLE_CLIENT_SECRET,
                }),
            ]
            : []),
        CredentialsProvider({
            id: "credentials",
            name: "Guest / Demo Account",
            credentials: {
                name: { label: "Name", type: "text", placeholder: "Alex" },
                email: { label: "Email", type: "email", placeholder: "alex@syncroom.dev" }
            },
            async authorize(credentials) {
                const name = credentials?.name?.trim() || "SyncRoom User";
                const email = credentials?.email?.trim() || "user@syncroom.dev";
                return {
                    id: "user_" + Math.random().toString(36).substring(2, 9),
                    name,
                    email,
                    image: `https://api.dicebear.com/7.x/adventurer/svg?seed=${encodeURIComponent(email)}`
                };
            }
        })
    ],
    pages: {
        signIn: '/', // Using landing page as sign-in
    },
    session: {
        strategy: "jwt" as const,
    },
    callbacks: {
        async jwt({ token, user }: { token: JWT, user?: User | AdapterUser }) {
            // If user exists, it means it's a new sign-in or a session refresh with a user object
            if (user) {
                token.id = user.id; // Attach user id to the token
            }
            return token;
        },
        async session({ session, token }: { session: Session, token: JWT }) {
            if (session.user) {
                // We'll attach the user id from token to the session user
                // eslint-disable-next-line @typescript-eslint/no-explicit-any
                (session.user as any).id = token.id; // Use token.id which we set in the jwt callback
            }
            return session;
        }
    }
};
